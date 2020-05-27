/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.core;

import static tech.pegasys.teku.core.results.AttestationProcessingResult.INVALID;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.teku.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.teku.util.config.Constants.SAFE_SLOTS_TO_UPDATE_JUSTIFIED;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.results.AttestationProcessingResult;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.data.BlockProcessingRecord;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;

public class ForkChoiceUtil {

  private static final Logger LOG = LogManager.getLogger();

  public static UnsignedLong get_slots_since_genesis(ReadOnlyStore store, boolean useUnixTime) {
    UnsignedLong time =
        useUnixTime ? UnsignedLong.valueOf(Instant.now().getEpochSecond()) : store.getTime();
    return getCurrentSlot(time, store.getGenesisTime());
  }

  public static UnsignedLong getCurrentSlot(UnsignedLong currentTime, UnsignedLong genesisTime) {
    return currentTime.minus(genesisTime).dividedBy(UnsignedLong.valueOf(SECONDS_PER_SLOT));
  }

  public static UnsignedLong get_current_slot(ReadOnlyStore store, boolean useUnixTime) {
    return UnsignedLong.valueOf(GENESIS_SLOT).plus(get_slots_since_genesis(store, useUnixTime));
  }

  public static UnsignedLong get_current_slot(ReadOnlyStore store) {
    return get_current_slot(store, false);
  }

  public static UnsignedLong compute_slots_since_epoch_start(UnsignedLong slot) {
    return slot.minus(compute_start_slot_at_epoch(compute_epoch_at_slot(slot)));
  }

  /**
   * Get the ancestor of ``block`` with slot number ``slot``.
   *
   * @param store
   * @param root
   * @param slot
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.10.1/specs/phase0/fork-choice.md#get_ancestor</a>
   */
  private static Bytes32 get_ancestor(ReadOnlyStore store, Bytes32 root, UnsignedLong slot) {
    BeaconBlock block = store.getBlock(root);
    if (block.getSlot().compareTo(slot) > 0) {
      return get_ancestor(store, block.getParent_root(), slot);
    } else if (block.getSlot().equals(slot)) {
      return root;
    } else {
      // root is older than the queried slot, thus a skip slot. Return earliest root prior to slot.
      return root;
    }
  }

  /*
  To address the bouncing attack, only update conflicting justified
  checkpoints in the fork choice if in the early slots of the epoch.
  Otherwise, delay incorporation of new justified checkpoint until next epoch boundary.
  See https://ethresear.ch/t/prevention-of-bouncing-attack-on-ffg/6114 for more detailed analysis and discussion.
  */

  private static boolean should_update_justified_checkpoint(
      ReadOnlyStore store, Checkpoint new_justified_checkpoint) {
    if (compute_slots_since_epoch_start(get_current_slot(store, true))
            .compareTo(UnsignedLong.valueOf(SAFE_SLOTS_TO_UPDATE_JUSTIFIED))
        < 0) {
      return true;
    }

    UnsignedLong justified_slot =
        compute_start_slot_at_epoch(store.getJustifiedCheckpoint().getEpoch());
    return get_ancestor(store, new_justified_checkpoint.getRoot(), justified_slot)
        .equals(store.getJustifiedCheckpoint().getRoot());
  }

  // Fork Choice Event Handlers

  /**
   * @param store
   * @param time
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_tick</a>
   */
  public static void on_tick(MutableStore store, UnsignedLong time) {
    UnsignedLong previous_slot = get_current_slot(store);

    // Update store time
    store.setTime(time);

    UnsignedLong current_slot = get_current_slot(store);

    // Not a new epoch, return
    if (!(current_slot.compareTo(previous_slot) > 0
        && compute_slots_since_epoch_start(current_slot).equals(UnsignedLong.ZERO))) {
      return;
    }

    // Update store.justified_checkpoint if a better checkpoint is known
    if (store
            .getBestJustifiedCheckpoint()
            .getEpoch()
            .compareTo(store.getJustifiedCheckpoint().getEpoch())
        > 0) {
      store.setJustifiedCheckpoint(store.getBestJustifiedCheckpoint());
    }
  }

  /**
   * @param store
   * @param signed_block
   * @param st
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_block</a>
   */
  @CheckReturnValue
  public static BlockImportResult on_block(
      final MutableStore store, final SignedBeaconBlock signed_block, final StateTransition st) {
    final BeaconBlock block = signed_block.getMessage();
    final BeaconState preState = store.getBlockState(block.getParent_root());

    // Return early if precondition checks fail;
    final Optional<BlockImportResult> maybeFailure = checkOnBlockConditions(block, preState, store);
    if (maybeFailure.isPresent()) {
      return maybeFailure.get();
    }

    Bytes32 blockRoot = block.hash_tree_root();
    // Add new block to the store
    store.putBlock(blockRoot, signed_block);

    // Make a copy of the state to avoid mutability issues
    BeaconState state;

    // Check the block is valid and compute the post-state
    try {
      state = st.initiate(preState, signed_block, true);
    } catch (StateTransitionException e) {
      return BlockImportResult.failedStateTransition(e);
    }

    // Add new state for this block to the store
    store.putBlockState(blockRoot, state);

    // Update justified checkpoint
    final Checkpoint justifiedCheckpoint = state.getCurrent_justified_checkpoint();
    if (justifiedCheckpoint.getEpoch().compareTo(store.getJustifiedCheckpoint().getEpoch()) > 0) {
      try {
        if (justifiedCheckpoint.getEpoch().compareTo(store.getBestJustifiedCheckpoint().getEpoch())
            > 0) {
          storeCheckpointState(
              store, st, justifiedCheckpoint, store.getBlockState(justifiedCheckpoint.getRoot()));
          store.setBestJustifiedCheckpoint(justifiedCheckpoint);
        }
        if (should_update_justified_checkpoint(store, justifiedCheckpoint)) {
          storeCheckpointState(
              store, st, justifiedCheckpoint, store.getBlockState(justifiedCheckpoint.getRoot()));
          store.setJustifiedCheckpoint(justifiedCheckpoint);
        }
      } catch (SlotProcessingException | EpochProcessingException e) {
        return BlockImportResult.failedStateTransition(e);
      }
    }

    // Update finalized checkpoint
    final Checkpoint finalizedCheckpoint = state.getFinalized_checkpoint();
    if (finalizedCheckpoint.getEpoch().compareTo(store.getFinalizedCheckpoint().getEpoch()) > 0) {
      try {
        storeCheckpointState(
            store, st, finalizedCheckpoint, store.getBlockState(finalizedCheckpoint.getRoot()));
      } catch (SlotProcessingException | EpochProcessingException e) {
        return BlockImportResult.failedStateTransition(e);
      }
      store.setFinalizedCheckpoint(finalizedCheckpoint);
      UnsignedLong finalized_slot = store.getFinalizedCheckpoint().getEpochStartSlot();
      // Update justified if new justified is later than store justified
      // or if store justified is not in chain with finalized checkpoint
      if (state
                  .getCurrent_justified_checkpoint()
                  .getEpoch()
                  .compareTo(store.getJustifiedCheckpoint().getEpoch())
              > 0
          || !get_ancestor(store, store.getJustifiedCheckpoint().getRoot(), finalized_slot)
              .equals(store.getFinalizedCheckpoint().getRoot())) {
        try {
          storeCheckpointState(
              store, st, finalizedCheckpoint, store.getBlockState(finalizedCheckpoint.getRoot()));
          store.setJustifiedCheckpoint(state.getCurrent_justified_checkpoint());
        } catch (SlotProcessingException | EpochProcessingException e) {
          return BlockImportResult.failedStateTransition(e);
        }
      }
    }

    final BlockProcessingRecord record = new BlockProcessingRecord(preState, signed_block, state);
    return BlockImportResult.successful(record);
  }

  private static Optional<BlockImportResult> checkOnBlockConditions(
      final BeaconBlock block, final BeaconState preState, final ReadOnlyStore store) {
    if (preState == null) {
      return Optional.of(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    if (preState.getSlot().compareTo(block.getSlot()) >= 0) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    if (blockIsFromFuture(block, store)) {
      return Optional.of(BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE);
    }
    if (!blockDescendsFromLatestFinalizedBlock(block, store)) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    return Optional.empty();
  }

  private static boolean blockIsFromFuture(BeaconBlock block, ReadOnlyStore store) {
    return get_current_slot(store).compareTo(block.getSlot()) < 0;
  }

  private static boolean blockDescendsFromLatestFinalizedBlock(
      final BeaconBlock block, final ReadOnlyStore store) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();

    // Make sure this block's slot is after the latest finalized slot
    final UnsignedLong finalizedEpochStartSlot = finalizedCheckpoint.getEpochStartSlot();
    if (block.getSlot().compareTo(finalizedEpochStartSlot) <= 0) {
      return false;
    }

    // Make sure this block descends from the finalized block
    final UnsignedLong finalizedSlot = store.getBlock(finalizedCheckpoint.getRoot()).getSlot();
    final Bytes32 ancestorAtFinalizedSlot =
        get_ancestor(store, block.getParent_root(), finalizedSlot);
    return ancestorAtFinalizedSlot.equals(finalizedCheckpoint.getRoot());
  }

  /**
   * Run ``on_attestation`` upon receiving a new ``attestation`` from either within a block or
   * directly on the wire.
   *
   * <p>An ``attestation`` that is asserted as invalid may be valid at a later time, consider
   * scheduling it for later processing in such case.
   *
   * @param store
   * @param attestation
   * @param stateTransition
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_attestation</a>
   */
  @CheckReturnValue
  public static AttestationProcessingResult on_attestation(
      final MutableStore store,
      final ValidateableAttestation validateableAttestation,
      final StateTransition stateTransition,
      final ForkChoiceStrategy forkChoiceStrategy) {

    Attestation attestation = validateableAttestation.getAttestation();
    Checkpoint target = attestation.getData().getTarget();

    return validateOnAttestation(store, attestation)
        .ifSuccessful(() -> storeTargetCheckpointState(store, stateTransition, target))
        .ifSuccessful(
            () -> {
              Optional<IndexedAttestation> maybeIndexedAttestation =
                  indexAndValidateAttestation(store, attestation, target);

              if (maybeIndexedAttestation.isEmpty()) {
                return INVALID;
              }

              IndexedAttestation indexedAttestation = maybeIndexedAttestation.get();
              AttestationProcessingResult result =
                  checkIfAttestationShouldBeSavedForFuture(store, attestation);

              if (result.isSuccessful()) {
                forkChoiceStrategy.onAttestation(store, indexedAttestation);
              } else {
                validateableAttestation.setIndexedAttestation(indexedAttestation);
              }

              return result;
            });
  }

  /**
   * Returns the indexed attestation if attestation is valid, else, returns an empty optional.
   *
   * @param store
   * @param attestation
   * @param target
   * @return
   */
  private static Optional<IndexedAttestation> indexAndValidateAttestation(
      MutableStore store, Attestation attestation, Checkpoint target) {
    BeaconState target_state = store.getCheckpointState(target);

    // Get state at the `target` to validate attestation and calculate the committees
    IndexedAttestation indexed_attestation;
    try {
      indexed_attestation = get_indexed_attestation(target_state, attestation);
    } catch (IllegalArgumentException e) {
      LOG.warn("on_attestation: Attestation is not valid: ", e);
      return Optional.empty();
    }
    if (!is_valid_indexed_attestation(target_state, indexed_attestation)) {
      LOG.warn("on_attestation: Attestation is not valid");
      return Optional.empty();
    }

    return Optional.of(indexed_attestation);
  }

  private static AttestationProcessingResult storeTargetCheckpointState(
      final MutableStore store, final StateTransition stateTransition, final Checkpoint target) {
    // Store target checkpoint state if not yet seen
    BeaconState targetRootState = store.getBlockState(target.getRoot());
    try {
      storeCheckpointState(store, stateTransition, target, targetRootState);
    } catch (SlotProcessingException | EpochProcessingException e) {
      LOG.warn("on_attestation: Attestation failed state transition. ", e);
      return AttestationProcessingResult.INVALID;
    }
    return SUCCESSFUL;
  }

  private static AttestationProcessingResult validateOnAttestation(
      final MutableStore store, final Attestation attestation) {
    final Checkpoint target = attestation.getData().getTarget();
    UnsignedLong current_epoch = compute_epoch_at_slot(get_current_slot(store));

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    UnsignedLong previous_epoch =
        current_epoch.compareTo(UnsignedLong.valueOf(GENESIS_EPOCH)) > 0
            ? current_epoch.minus(UnsignedLong.ONE)
            : UnsignedLong.valueOf(GENESIS_EPOCH);

    if (!target.getEpoch().equals(previous_epoch) && !target.getEpoch().equals(current_epoch)) {
      LOG.warn("on_attestation: Attestations must be from the current or previous epoch");
      return AttestationProcessingResult.INVALID;
    }

    if (!target.getEpoch().equals(compute_epoch_at_slot(attestation.getData().getSlot()))) {
      LOG.warn("on_attestation: Attestation slot must be within specified epoch");
      return AttestationProcessingResult.INVALID;
    }

    if (!store.getBlockRoots().contains(target.getRoot())) {
      // Attestations target must be for a known block. If a target block is unknown, delay
      // consideration until the block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    if (!store.getBlockRoots().contains(attestation.getData().getBeacon_block_root())) {
      // Attestations must be for a known block. If block is unknown, delay consideration until the
      // block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    if (store
            .getBlock(attestation.getData().getBeacon_block_root())
            .getSlot()
            .compareTo(attestation.getData().getSlot())
        > 0) {
      LOG.warn(
          "on_attestation: Attestations must not be for blocks in the future. If not, the attestation should not be considered");
      return AttestationProcessingResult.INVALID;
    }

    return SUCCESSFUL;
  }

  private static AttestationProcessingResult checkIfAttestationShouldBeSavedForFuture(
      MutableStore store, Attestation attestation) {

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    if (get_current_slot(store).compareTo(attestation.getData().getSlot()) <= 0) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    if (get_current_slot(store).compareTo(attestation.getData().getTarget().getEpochStartSlot())
        < 0) {
      return AttestationProcessingResult.SAVED_FOR_FUTURE;
    }
    return SUCCESSFUL;
  }

  private static void storeCheckpointState(
      final MutableStore store,
      final StateTransition stateTransition,
      final Checkpoint target,
      final BeaconState targetRootState)
      throws SlotProcessingException, EpochProcessingException {
    if (!store.containsCheckpointState(target)) {
      final BeaconState targetState;
      if (target.getEpochStartSlot().equals(targetRootState.getSlot())) {
        targetState = targetRootState;
      } else {
        targetState = stateTransition.process_slots(targetRootState, target.getEpochStartSlot());
      }
      store.putCheckpointState(target, targetState);
    }
  }
}
