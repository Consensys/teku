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

package tech.pegasys.artemis.statetransition.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SAFE_SLOTS_TO_UPDATE_JUSTIFIED;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.storage.ReadOnlyStore;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.Store.Transaction;

public class ForkChoiceUtil {

  public static UnsignedLong get_current_slot(Store.Transaction store, boolean useUnixTime) {
    UnsignedLong time =
        useUnixTime ? UnsignedLong.valueOf(Instant.now().getEpochSecond()) : store.getTime();
    return time.minus(store.getGenesisTime()).dividedBy(UnsignedLong.valueOf(SECONDS_PER_SLOT));
  }

  public static UnsignedLong get_current_slot(Store.Transaction store) {
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
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#get_ancestor</a>
   */
  public static Bytes32 get_ancestor(ReadOnlyStore store, Bytes32 root, UnsignedLong slot) {
    BeaconBlock block = store.getBlock(root);
    if (block.getSlot().compareTo(slot) > 0) {
      return get_ancestor(store, block.getParent_root(), slot);
    } else if (block.getSlot().equals(slot)) {
      return root;
    } else {
      return Bytes32.ZERO;
    }
  }

  /**
   * Gets the latest attesting balance for the given block root
   *
   * @param store
   * @param root
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#get_latest_attesting_balance</a>
   */
  public static UnsignedLong get_latest_attesting_balance(Store store, Bytes32 root) {
    BeaconState state = store.getCheckpointState(store.getJustifiedCheckpoint());
    List<Integer> active_indices = get_active_validator_indices(state, get_current_epoch(state));
    return UnsignedLong.valueOf(
        active_indices.stream()
            .filter(
                i ->
                    store.containsLatestMessage(UnsignedLong.valueOf(i))
                        && get_ancestor(
                                store,
                                store.getLatestMessage(UnsignedLong.valueOf(i)).getRoot(),
                                store.getBlock(root).getSlot())
                            .equals(root))
            .map(i -> state.getValidators().get(i).getEffective_balance())
            .mapToLong(UnsignedLong::longValue)
            .sum());
  }

  /**
   * Gets the root of the head block according to LMD-GHOST
   *
   * @param store
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#get_head</a>
   */
  public static Bytes32 get_head(Store store) {
    // Execute the LMD-GHOST fork choice
    Bytes32 head = store.getJustifiedCheckpoint().getRoot();
    UnsignedLong justified_slot = store.getJustifiedCheckpoint().getEpochSlot();

    while (true) {
      final Bytes32 head_in_filter = head;
      List<Bytes32> children =
          store.getBlockRoots().stream()
              .filter(
                  root ->
                      store.getBlock(root).getParent_root().equals(head_in_filter)
                          && store.getBlock(root).getSlot().compareTo(justified_slot) > 0)
              .collect(Collectors.toList());

      if (children.size() == 0) {
        return head;
      }

      // Sort by latest attesting balance with ties broken lexicographically
      UnsignedLong max_value = UnsignedLong.ZERO;
      for (Bytes32 child : children) {
        UnsignedLong curr_value = get_latest_attesting_balance(store, child);
        if (curr_value.compareTo(max_value) > 0) {
          max_value = curr_value;
        }
      }

      final UnsignedLong max = max_value;
      head =
          children.stream()
              .filter(child -> get_latest_attesting_balance(store, child).compareTo(max) == 0)
              .max(Comparator.comparing(Bytes::toHexString))
              .get();
    }
  }

  /*
  To address the bouncing attack, only update conflicting justified
  checkpoints in the fork choice if in the early slots of the epoch.
  Otherwise, delay incorporation of new justified checkpoint until next epoch boundary.
  See https://ethresear.ch/t/prevention-of-bouncing-attack-on-ffg/6114 for more detailed analysis and discussion.
  */
  public static boolean should_update_justified_checkpoint(
      Store.Transaction store, Checkpoint new_justified_checkpoint) {
    if (compute_slots_since_epoch_start(get_current_slot(store, true))
            .compareTo(UnsignedLong.valueOf(SAFE_SLOTS_TO_UPDATE_JUSTIFIED))
        < 0) {
      return true;
    }

    BeaconBlock new_justified_block = store.getBlock(new_justified_checkpoint.getRoot());
    if (new_justified_block.getSlot().compareTo(store.getJustifiedCheckpoint().getEpochSlot())
        <= 0) {
      return false;
    }
    if (!get_ancestor(
            store,
            new_justified_checkpoint.getRoot(),
            store.getBlock(store.getJustifiedCheckpoint().getRoot()).getSlot())
        .equals(store.getJustifiedCheckpoint().getRoot())) {
      return false;
    }

    return true;
  }

  // Fork Choice Event Handlers

  /**
   * @param store
   * @param time
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_tick</a>
   */
  public static void on_tick(Store.Transaction store, UnsignedLong time) {
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
   * @param block
   * @param st
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_block</a>
   */
  @CheckReturnValue
  public static BlockImportResult on_block(
      Store.Transaction store, BeaconBlock block, StateTransition st) {
    final BeaconState preState = store.getBlockState(block.getParent_root());

    // Return early if precondition checks fail;
    final Optional<BlockImportResult> maybeFailure =
        checkOnBlockPreconditions(block, preState, store);
    if (maybeFailure.isPresent()) {
      return maybeFailure.get();
    }

    // Add new block to the store
    store.putBlock(block.signing_root("signature"), block);

    // Make a copy of the state to avoid mutability issues
    BeaconStateWithCache state =
        BeaconStateWithCache.deepCopy(BeaconStateWithCache.fromBeaconState(preState));

    // Check the block is valid and compute the post-state
    try {
      state = st.initiate(state, block, true);
    } catch (StateTransitionException e) {
      return BlockImportResult.failedStateTransition(e);
    }

    // Add new state for this block to the store
    store.putBlockState(block.signing_root("signature"), state);

    // Update justified checkpoint
    final Checkpoint justifiedCheckpoint = state.getCurrent_justified_checkpoint();
    if (justifiedCheckpoint.getEpoch().compareTo(store.getJustifiedCheckpoint().getEpoch()) > 0) {
      try {
        storeCheckpointState(
            store, st, justifiedCheckpoint, store.getBlockState(justifiedCheckpoint.getRoot()));
      } catch (SlotProcessingException | EpochProcessingException e) {
        return BlockImportResult.failedStateTransition(e);
      }
      store.setBestJustifiedCheckpoint(justifiedCheckpoint);
      if (should_update_justified_checkpoint(store, justifiedCheckpoint)) {
        store.setJustifiedCheckpoint(justifiedCheckpoint);
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
    }

    final BlockProcessingRecord record = new BlockProcessingRecord(preState, block, state);
    return BlockImportResult.successful(record);
  }

  private static Optional<BlockImportResult> checkOnBlockPreconditions(
      final BeaconBlock block, final BeaconState preState, final ReadOnlyStore store) {
    if (preState == null) {
      return Optional.of(BlockImportResult.FAILED_UNKNOWN_PARENT);
    }
    if (blockIsFromFuture(block, preState, store)) {
      return Optional.of(BlockImportResult.FAILED_BLOCK_IS_FROM_FUTURE);
    }
    if (!blockDescendsFromLatestFinalizedBlock(block, store)) {
      return Optional.of(BlockImportResult.FAILED_INVALID_ANCESTRY);
    }
    return Optional.empty();
  }

  private static boolean blockIsFromFuture(
      BeaconBlock block, final BeaconState preState, ReadOnlyStore store) {
    final UnsignedLong genesisTime = preState.getGenesis_time();
    final UnsignedLong now = store.getTime();
    final UnsignedLong blockTime = getBlockTime(block, genesisTime);
    return now.compareTo(blockTime) < 0;
  }

  private static UnsignedLong getBlockTime(BeaconBlock block, UnsignedLong genesisTime) {
    return genesisTime.plus(block.getSlot().times(UnsignedLong.valueOf(SECONDS_PER_SLOT)));
  }

  private static boolean blockDescendsFromLatestFinalizedBlock(
      final BeaconBlock block, final ReadOnlyStore store) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();

    // Make sure this block's slot is after the latest finalized slot
    final UnsignedLong finalizedEpochStartSlot = finalizedCheckpoint.getEpochSlot();
    if (block.getSlot().compareTo(finalizedEpochStartSlot) <= 0) {
      return false;
    }

    // Make sure this block descends from the finalized block
    final UnsignedLong finalizedSlot = store.getBlock(finalizedCheckpoint.getRoot()).getSlot();
    final Bytes32 ancestorAtFinalizedSlot =
        get_ancestor(store, block.getParent_root(), finalizedSlot);
    if (!ancestorAtFinalizedSlot.equals(finalizedCheckpoint.getRoot())) {
      return false;
    }

    return true;
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
  public static void on_attestation(
      Store.Transaction store, Attestation attestation, StateTransition stateTransition)
      throws SlotProcessingException, EpochProcessingException {

    Checkpoint target = attestation.getData().getTarget();

    UnsignedLong current_epoch = compute_epoch_at_slot(get_current_slot(store, true));

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    UnsignedLong previous_epoch =
        current_epoch.compareTo(UnsignedLong.valueOf(GENESIS_EPOCH)) > 0
            ? current_epoch.minus(UnsignedLong.ONE)
            : UnsignedLong.valueOf(GENESIS_EPOCH);

    List<UnsignedLong> epochs = List.of(current_epoch, previous_epoch);
    checkArgument(
        epochs.contains(target.getEpoch()),
        "on_attestation: Attestations must be from the current or previous epoch");

    checkArgument(
        store.containsBlock(target.getRoot()),
        "on_attestation: Cannot calculate the current shuffling if have not seen the target");

    checkArgument(
        store.getBlockRoots().contains(target.getRoot()),
        "on_attestation: Attestations target must be for a known block. If a target block is unknown, delay consideration until the block is found");

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    BeaconState targetRootState = store.getBlockState(target.getRoot());
    UnsignedLong unixTimeStamp = UnsignedLong.valueOf(Instant.now().getEpochSecond());
    checkArgument(
        unixTimeStamp.compareTo(
                targetRootState
                    .getGenesis_time()
                    .plus(target.getEpochSlot().times(UnsignedLong.valueOf(SECONDS_PER_SLOT))))
            >= 0,
        "on_attestation: Attestations cannot be from the future epochs");

    checkArgument(
        store.getBlockRoots().contains(attestation.getData().getBeacon_block_root()),
        "on_attestation: Attestations must be for a known block. If block is unknown, delay consideration until the block is found");

    checkArgument(
        store
                .getBlock(attestation.getData().getBeacon_block_root())
                .getSlot()
                .compareTo(attestation.getData().getSlot())
            <= 0,
        "on_attestation: Attestations must not be for blocks in the future. If not, the attestation should not be considered");

    // Store target checkpoint state if not yet seen
    storeCheckpointState(store, stateTransition, target, targetRootState);
    BeaconState target_state = store.getCheckpointState(target);

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    UnsignedLong attestation_slot = attestation.getData().getSlot();
    checkArgument(
        unixTimeStamp.compareTo(
                attestation_slot
                    .plus(UnsignedLong.ONE)
                    .times(UnsignedLong.valueOf(SECONDS_PER_SLOT)))
            >= 0,
        "on_attestation: Attestation can only affect the forck choice of subsequent slots");

    // Get state at the `target` to validate attestation and calculate the committees
    IndexedAttestation indexed_attestation = get_indexed_attestation(target_state, attestation);
    checkArgument(
        is_valid_indexed_attestation(target_state, indexed_attestation),
        "on_attestation: Attestation is not valid");

    // Update latest messages
    for (UnsignedLong i : indexed_attestation.getAttesting_indices()) {
      if (!store.containsLatestMessage(i)
          || target.getEpoch().compareTo(store.getLatestMessage(i).getEpoch()) > 0) {
        store.putLatestMessage(
            i, new Checkpoint(target.getEpoch(), attestation.getData().getBeacon_block_root()));
      }
    }
  }

  private static void storeCheckpointState(
      final Transaction store,
      final StateTransition stateTransition,
      final Checkpoint target,
      final BeaconState targetRootState)
      throws SlotProcessingException, EpochProcessingException {
    if (!store.containsCheckpointState(target)) {
      final BeaconState targetState;
      if (target.getEpochSlot().equals(targetRootState.getSlot())) {
        targetState = targetRootState;
      } else {
        final BeaconStateWithCache base_state = BeaconStateWithCache.deepCopy(targetRootState);
        stateTransition.process_slots(base_state, target.getEpochSlot(), false);
        targetState = base_state;
      }
      store.putCheckpointState(target, targetState);
    }
  }
}
