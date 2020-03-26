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

import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.util.config.Constants.SAFE_SLOTS_TO_UPDATE_JUSTIFIED;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckReturnValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.attestation.AttestationProcessingResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.storage.ReadOnlyStore;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.Store.Transaction;

public class ForkChoiceUtil {
  public static UnsignedLong get_slots_since_genesis(ReadOnlyStore store, boolean useUnixTime) {
    UnsignedLong time =
        useUnixTime ? UnsignedLong.valueOf(Instant.now().getEpochSecond()) : store.getTime();
    return time.minus(store.getGenesisTime()).dividedBy(UnsignedLong.valueOf(SECONDS_PER_SLOT));
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
  public static Bytes32 get_ancestor(ReadOnlyStore store, Bytes32 root, UnsignedLong slot) {
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
    return active_indices.stream()
        .filter(
            i ->
                store.containsLatestMessage(UnsignedLong.valueOf(i))
                    && get_ancestor(
                            store,
                            store.getLatestMessage(UnsignedLong.valueOf(i)).getRoot(),
                            store.getBlock(root).getSlot())
                        .equals(root))
        .map(i -> state.getValidators().get(i).getEffective_balance())
        .reduce(UnsignedLong.ZERO, UnsignedLong::plus);
  }

  public static boolean filter_block_tree(
      ReadOnlyStore store, Bytes32 block_root, Map<Bytes32, BeaconBlock> blocks) {
    BeaconBlock block = store.getBlock(block_root);
    List<Bytes32> children =
        store.getBlockRoots().stream()
            .filter(root -> store.getBlock(root).getParent_root().equals(block_root))
            .collect(Collectors.toList());
    // If any children branches contain expected finalized/justified checkpoints,
    // add to filtered block-tree and signal viability to parent
    if (!children.isEmpty()) {
      boolean filter_block_tree_result =
          children.stream()
              .map(child -> filter_block_tree(store, child, blocks))
              .reduce((a, b) -> a || b)
              .orElse(false);
      if (filter_block_tree_result) {
        blocks.put(block_root, block);
        return true;
      }
      return false;
    }

    BeaconState head_state = store.getBlockState(block_root);
    boolean correct_justified =
        store.getJustifiedCheckpoint().getEpoch().equals(UnsignedLong.valueOf(GENESIS_EPOCH))
            || head_state.getCurrent_justified_checkpoint().equals(store.getJustifiedCheckpoint());
    boolean correct_finalized =
        store.getFinalizedCheckpoint().getEpoch().equals(UnsignedLong.valueOf(GENESIS_EPOCH))
            || head_state.getFinalized_checkpoint().equals(store.getFinalizedCheckpoint());

    if (correct_justified && correct_finalized) {
      blocks.put(block_root, block);
      return true;
    }
    return false;
  }

  /**
   * Retrieve a filtered block tree from store, only returning branches whose leaf state's
   * justified/finalized info agrees with that in store.
   *
   * @param store
   * @return
   */
  public static Map<Bytes32, BeaconBlock> get_filtered_block_tree(Store store) {
    Bytes32 base = store.getJustifiedCheckpoint().getRoot();
    Map<Bytes32, BeaconBlock> blocks = new HashMap<>();
    filter_block_tree(store, base, blocks);
    return blocks;
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
    // Get filtered block tree that only includes viable branches
    final Map<Bytes32, BeaconBlock> blocks = get_filtered_block_tree(store);

    // Execute the LMD-GHOST fork choice
    Bytes32 head = store.getJustifiedCheckpoint().getRoot();
    UnsignedLong justified_slot = store.getJustifiedCheckpoint().getEpochStartSlot();

    while (true) {
      final Bytes32 head_in_filter = head;
      List<Bytes32> children =
          blocks.entrySet().stream()
              .filter(
                  (entry) -> {
                    final BeaconBlock block = entry.getValue();
                    return block.getParent_root().equals(head_in_filter)
                        && block.getSlot().compareTo(justified_slot) > 0;
                  })
              .map(Entry::getKey)
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
   * @param signed_block
   * @param st
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_block</a>
   */
  @CheckReturnValue
  public static BlockImportResult on_block(
      Store.Transaction store, SignedBeaconBlock signed_block, StateTransition st) {
    final BeaconBlock block = signed_block.getMessage();
    final BeaconState preState = store.getBlockState(block.getParent_root());

    // Return early if precondition checks fail;
    final Optional<BlockImportResult> maybeFailure = checkOnBlockConditions(block, preState, store);
    if (maybeFailure.isPresent()) {
      return maybeFailure.get();
    }

    // Add new block to the store
    store.putBlock(block.hash_tree_root(), signed_block);

    // Make a copy of the state to avoid mutability issues
    BeaconState state;

    // Check the block is valid and compute the post-state
    try {
      state = st.initiate(preState, signed_block, true);
    } catch (StateTransitionException e) {
      return BlockImportResult.failedStateTransition(e);
    }

    // Add new state for this block to the store
    store.putBlockState(block.hash_tree_root(), state);

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
      Store.Transaction store, Attestation attestation, StateTransition stateTransition) {

    Checkpoint target = attestation.getData().getTarget();

    UnsignedLong current_epoch = compute_epoch_at_slot(get_current_slot(store));

    // Use GENESIS_EPOCH for previous when genesis to avoid underflow
    UnsignedLong previous_epoch =
        current_epoch.compareTo(UnsignedLong.valueOf(GENESIS_EPOCH)) > 0
            ? current_epoch.minus(UnsignedLong.ONE)
            : UnsignedLong.valueOf(GENESIS_EPOCH);

    if (!target.getEpoch().equals(previous_epoch) && !target.getEpoch().equals(current_epoch)) {
      return AttestationProcessingResult.invalid(
          "on_attestation: Attestations must be from the current or previous epoch");
    }

    if (!target.getEpoch().equals(compute_epoch_at_slot(attestation.getData().getSlot()))) {
      return AttestationProcessingResult.invalid(
          "on_attestation: Attestation slot must be within specified epoch");
    }

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    if (get_current_slot(store).compareTo(attestation.getData().getSlot()) <= 0) {
      return AttestationProcessingResult.FAILED_NOT_FROM_PAST;
    }

    if (!store.getBlockRoots().contains(target.getRoot())) {
      // Attestations target must be for a known block. If a target block is unknown, delay
      // consideration until the block is found
      return AttestationProcessingResult.FAILED_UNKNOWN_BLOCK;
    }

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    if (get_current_slot(store).compareTo(target.getEpochStartSlot()) < 0) {
      return AttestationProcessingResult.FAILED_FUTURE_EPOCH;
    }

    if (!store.getBlockRoots().contains(attestation.getData().getBeacon_block_root())) {
      // Attestations must be for a known block. If block is unknown, delay consideration until the
      // block is found
      return AttestationProcessingResult.FAILED_UNKNOWN_BLOCK;
    }

    if (store
            .getBlock(attestation.getData().getBeacon_block_root())
            .getSlot()
            .compareTo(attestation.getData().getSlot())
        > 0) {
      return AttestationProcessingResult.invalid(
          "on_attestation: Attestations must not be for blocks in the future. If not, the attestation should not be considered");
    }

    // Store target checkpoint state if not yet seen
    BeaconState targetRootState = store.getBlockState(target.getRoot());
    try {
      storeCheckpointState(store, stateTransition, target, targetRootState);
    } catch (SlotProcessingException e) {
      return AttestationProcessingResult.failedStateTransition(e);
    } catch (EpochProcessingException e) {
      return AttestationProcessingResult.failedStateTransition(e);
    }
    BeaconState target_state = store.getCheckpointState(target);

    // Get state at the `target` to validate attestation and calculate the committees
    IndexedAttestation indexed_attestation = get_indexed_attestation(target_state, attestation);
    if (!is_valid_indexed_attestation(target_state, indexed_attestation)) {
      return AttestationProcessingResult.invalid("on_attestation: Attestation is not valid");
    }

    // Update latest messages
    for (UnsignedLong i : indexed_attestation.getAttesting_indices()) {
      if (!store.containsLatestMessage(i)
          || target.getEpoch().compareTo(store.getLatestMessage(i).getEpoch()) > 0) {
        store.putLatestMessage(
            i, new Checkpoint(target.getEpoch(), attestation.getData().getBeacon_block_root()));
      }
    }
    return AttestationProcessingResult.SUCCESSFUL;
  }

  private static void storeCheckpointState(
      final Transaction store,
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
