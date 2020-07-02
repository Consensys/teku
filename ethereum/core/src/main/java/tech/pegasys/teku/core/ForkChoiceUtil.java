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

import static tech.pegasys.teku.datastructures.util.AttestationProcessingResult.SUCCESSFUL;
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
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.data.BlockProcessingRecord;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.InvalidCheckpointException;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
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

  public static UnsignedLong getSlotStartTime(UnsignedLong slotNumber, UnsignedLong genesisTime) {
    return genesisTime.plus(slotNumber.times(UnsignedLong.valueOf(SECONDS_PER_SLOT)));
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
   * @param forkChoiceStrategy
   * @param root
   * @param slot
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.10.1/specs/phase0/fork-choice.md#get_ancestor</a>
   */
  public static Optional<Bytes32> get_ancestor(
      ForkChoiceStrategy forkChoiceStrategy, Bytes32 root, UnsignedLong slot) {
    Bytes32 parentRoot = root;
    Optional<UnsignedLong> blockSlot = forkChoiceStrategy.blockSlot(root);
    while (blockSlot.isPresent() && blockSlot.get().compareTo(slot) > 0) {
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      blockSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    return blockSlot.isPresent() ? Optional.of(parentRoot) : Optional.empty();
  }

  public static NavigableMap<UnsignedLong, Bytes32> getAncestors(
      ForkChoiceStrategy forkChoiceStrategy,
      Bytes32 root,
      UnsignedLong startSlot,
      UnsignedLong step,
      UnsignedLong count) {
    final NavigableMap<UnsignedLong, Bytes32> roots = new TreeMap<>();
    // minus(ONE) because the start block is included
    final UnsignedLong endSlot = startSlot.plus(step.times(count)).minus(UnsignedLong.ONE);
    Bytes32 parentRoot = root;
    Optional<UnsignedLong> parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    while (parentSlot.isPresent() && parentSlot.get().compareTo(startSlot) > 0) {
      maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
      parentRoot = forkChoiceStrategy.blockParentRoot(parentRoot).orElseThrow();
      parentSlot = forkChoiceStrategy.blockSlot(parentRoot);
    }
    maybeAddRoot(startSlot, step, roots, endSlot, parentRoot, parentSlot);
    return roots;
  }

  private static void maybeAddRoot(
      final UnsignedLong startSlot,
      final UnsignedLong step,
      final NavigableMap<UnsignedLong, Bytes32> roots,
      final UnsignedLong endSlot,
      final Bytes32 root,
      final Optional<UnsignedLong> maybeSlot) {
    maybeSlot.ifPresent(
        slot -> {
          if (slot.compareTo(endSlot) <= 0
              && slot.minus(startSlot).mod(step).equals(UnsignedLong.ZERO)) {
            roots.put(slot, root);
          }
        });
  }

  /*
  To address the bouncing attack, only update conflicting justified
  checkpoints in the fork choice if in the early slots of the epoch.
  Otherwise, delay incorporation of new justified checkpoint until next epoch boundary.
  See https://ethresear.ch/t/prevention-of-bouncing-attack-on-ffg/6114 for more detailed analysis and discussion.
  */

  private static boolean should_update_justified_checkpoint(
      ReadOnlyStore store,
      Checkpoint new_justified_checkpoint,
      ForkChoiceStrategy forkChoiceStrategy) {
    if (compute_slots_since_epoch_start(get_current_slot(store, true))
            .compareTo(UnsignedLong.valueOf(SAFE_SLOTS_TO_UPDATE_JUSTIFIED))
        < 0) {
      return true;
    }

    UnsignedLong justifiedSlot =
        compute_start_slot_at_epoch(store.getJustifiedCheckpoint().getEpoch());
    return hasAncestorAtSlot(
        forkChoiceStrategy,
        new_justified_checkpoint.getRoot(),
        justifiedSlot,
        store.getJustifiedCheckpoint().getRoot());
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
      final MutableStore store,
      final SignedBeaconBlock signed_block,
      final StateTransition st,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final BeaconBlock block = signed_block.getMessage();
    final BeaconState preState = store.getBlockState(block.getParent_root());

    // Return early if precondition checks fail;
    final Optional<BlockImportResult> maybeFailure =
        checkOnBlockConditions(block, preState, store, forkChoiceStrategy);
    if (maybeFailure.isPresent()) {
      return maybeFailure.get();
    }

    // Make a copy of the state to avoid mutability issues
    BeaconState state;

    // Check the block is valid and compute the post-state
    try {
      state = st.initiate(preState, signed_block, true);
    } catch (StateTransitionException e) {
      return BlockImportResult.failedStateTransition(e);
    }

    // Add new block to store
    store.putBlockAndState(signed_block, state);

    // Update justified checkpoint
    final Checkpoint justifiedCheckpoint = state.getCurrent_justified_checkpoint();
    if (justifiedCheckpoint.getEpoch().compareTo(store.getJustifiedCheckpoint().getEpoch()) > 0) {
      if (justifiedCheckpoint.getEpoch().compareTo(store.getBestJustifiedCheckpoint().getEpoch())
          > 0) {
        store.setBestJustifiedCheckpoint(justifiedCheckpoint);
      }
      if (should_update_justified_checkpoint(store, justifiedCheckpoint, forkChoiceStrategy)) {
        store.setJustifiedCheckpoint(justifiedCheckpoint);
      }
    }

    // Update finalized checkpoint
    final Checkpoint finalizedCheckpoint = state.getFinalized_checkpoint();
    if (finalizedCheckpoint.getEpoch().compareTo(store.getFinalizedCheckpoint().getEpoch()) > 0) {
      store.setFinalizedCheckpoint(finalizedCheckpoint);
      // Update justified if new justified is later than store justified
      // or if store justified is not in chain with finalized checkpoint
      if (state
                  .getCurrent_justified_checkpoint()
                  .getEpoch()
                  .compareTo(store.getJustifiedCheckpoint().getEpoch())
              > 0
          || !isFinalizedAncestorOfJustified(forkChoiceStrategy, store)) {
        store.setJustifiedCheckpoint(state.getCurrent_justified_checkpoint());
      }
    }

    final BlockProcessingRecord record = new BlockProcessingRecord(preState, signed_block, state);
    return BlockImportResult.successful(record);
  }

  private static boolean isFinalizedAncestorOfJustified(
      ForkChoiceStrategy forkChoiceStrategy, ReadOnlyStore store) {
    UnsignedLong finalizedSlot = store.getFinalizedCheckpoint().getEpochStartSlot();
    return hasAncestorAtSlot(
        forkChoiceStrategy,
        store.getJustifiedCheckpoint().getRoot(),
        finalizedSlot,
        store.getFinalizedCheckpoint().getRoot());
  }

  private static Optional<BlockImportResult> checkOnBlockConditions(
      final BeaconBlock block,
      final BeaconState preState,
      final ReadOnlyStore store,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final UnsignedLong blockSlot = block.getSlot();
    if (preState == null) {
      return Optional.of(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    if (preState.getSlot().compareTo(blockSlot) >= 0) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    if (blockIsFromFuture(store, blockSlot)) {
      return Optional.of(BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE);
    }
    if (!blockDescendsFromLatestFinalizedBlock(block, store, forkChoiceStrategy)) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    return Optional.empty();
  }

  private static boolean blockIsFromFuture(ReadOnlyStore store, final UnsignedLong blockSlot) {
    return get_current_slot(store).compareTo(blockSlot) < 0;
  }

  private static boolean hasAncestorAtSlot(
      ForkChoiceStrategy forkChoiceStrategy,
      Bytes32 root,
      UnsignedLong slot,
      Bytes32 ancestorRoot) {
    return get_ancestor(forkChoiceStrategy, root, slot)
        .map(ancestorAtSlot -> ancestorAtSlot.equals(ancestorRoot))
        .orElse(false);
  }

  private static boolean blockDescendsFromLatestFinalizedBlock(
      final BeaconBlock block,
      final ReadOnlyStore store,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();
    final UnsignedLong blockSlot = block.getSlot();

    // Make sure this block's slot is after the latest finalized slot
    final UnsignedLong finalizedEpochStartSlot = finalizedCheckpoint.getEpochStartSlot();
    if (blockSlot.compareTo(finalizedEpochStartSlot) <= 0) {
      return false;
    }

    // Make sure this block descends from the finalized block
    final UnsignedLong finalizedSlot =
        forkChoiceStrategy.blockSlot(finalizedCheckpoint.getRoot()).orElseThrow();
    return hasAncestorAtSlot(
        forkChoiceStrategy, block.getParent_root(), finalizedSlot, finalizedCheckpoint.getRoot());
  }

  /**
   * Run ``on_attestation`` upon receiving a new ``attestation`` from either within a block or
   * directly on the wire.
   *
   * <p>An ``attestation`` that is asserted as invalid may be valid at a later time, consider
   * scheduling it for later processing in such case.
   *
   * @param store
   * @param validateableAttestation
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

    return validateOnAttestation(store, attestation, forkChoiceStrategy)
        .ifSuccessful(() -> indexAndValidateAttestation(store, validateableAttestation, target))
        .ifSuccessful(() -> checkIfAttestationShouldBeSavedForFuture(store, attestation))
        .ifSuccessful(
            () -> {
              IndexedAttestation indexedAttestation =
                  validateableAttestation.getIndexedAttestation();
              forkChoiceStrategy.onAttestation(store, indexedAttestation);
              return SUCCESSFUL;
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
  private static AttestationProcessingResult indexAndValidateAttestation(
      MutableStore store, ValidateableAttestation attestation, Checkpoint target) {
    BeaconState target_state;
    try {
      target_state = store.getCheckpointState(target);
    } catch (final InvalidCheckpointException e) {
      LOG.debug("on_attestation: Attestation target checkpoint is invalid", e);
      return AttestationProcessingResult.invalid("Invalid target checkpoint: " + e.getMessage());
    }

    // Get state at the `target` to validate attestation and calculate the committees
    IndexedAttestation indexed_attestation;
    try {
      indexed_attestation = get_indexed_attestation(target_state, attestation.getAttestation());
    } catch (IllegalArgumentException e) {
      LOG.debug("on_attestation: Attestation is not valid: ", e);
      return AttestationProcessingResult.invalid(e.getMessage());
    }
    return is_valid_indexed_attestation(target_state, indexed_attestation)
        .ifSuccessful(
            () -> {
              attestation.setIndexedAttestation(indexed_attestation);
              return SUCCESSFUL;
            });
  }

  private static AttestationProcessingResult validateOnAttestation(
      final MutableStore store,
      final Attestation attestation,
      final ForkChoiceStrategy forkChoiceStrategy) {
    final Checkpoint target = attestation.getData().getTarget();
    UnsignedLong current_epoch = compute_epoch_at_slot(get_current_slot(store));

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    UnsignedLong previous_epoch =
        current_epoch.compareTo(UnsignedLong.valueOf(GENESIS_EPOCH)) > 0
            ? current_epoch.minus(UnsignedLong.ONE)
            : UnsignedLong.valueOf(GENESIS_EPOCH);

    if (!target.getEpoch().equals(previous_epoch) && !target.getEpoch().equals(current_epoch)) {
      return AttestationProcessingResult.invalid(
          "Attestations must be from the current or previous epoch");
    }

    if (!target.getEpoch().equals(compute_epoch_at_slot(attestation.getData().getSlot()))) {
      return AttestationProcessingResult.invalid("Attestation slot must be within specified epoch");
    }

    if (!forkChoiceStrategy.contains(target.getRoot())) {
      // Attestations target must be for a known block. If a target block is unknown, delay
      // consideration until the block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    Optional<UnsignedLong> blockSlot =
        forkChoiceStrategy.blockSlot(attestation.getData().getBeacon_block_root());
    if (blockSlot.isEmpty()) {
      // Attestations must be for a known block. If block is unknown, delay consideration until the
      // block is found
      return AttestationProcessingResult.UNKNOWN_BLOCK;
    }

    if (blockSlot.get().compareTo(attestation.getData().getSlot()) > 0) {
      return AttestationProcessingResult.invalid(
          "Attestations must not be for blocks in the future. If not, the attestation should not be considered");
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
}
