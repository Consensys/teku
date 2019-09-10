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
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_attestation_data_slot;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_of_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
import tech.pegasys.artemis.storage.LatestMessage;
import tech.pegasys.artemis.storage.ReadOnlyStore;
import tech.pegasys.artemis.storage.Store;

public class ForkChoiceUtil {

  public static Store get_genesis_store(BeaconStateWithCache genesis_state) {
    BeaconBlock genesis_block = new BeaconBlock(genesis_state.hash_tree_root());
    Bytes32 root = genesis_block.signing_root("signature");
    Checkpoint justified_checkpoint = new Checkpoint(UnsignedLong.valueOf(GENESIS_EPOCH), root);
    Checkpoint finalized_checkpoint = new Checkpoint(UnsignedLong.valueOf(GENESIS_EPOCH), root);
    ConcurrentHashMap<Bytes32, BeaconBlock> blocks = new ConcurrentHashMap<>();
    ConcurrentHashMap<Bytes32, BeaconState> block_states = new ConcurrentHashMap<>();
    ConcurrentHashMap<Checkpoint, BeaconState> checkpoint_states = new ConcurrentHashMap<>();
    blocks.put(root, genesis_block);
    block_states.put(root, new BeaconStateWithCache(genesis_state));
    checkpoint_states.put(justified_checkpoint, new BeaconStateWithCache(genesis_state));
    return new Store(
        genesis_state.getGenesis_time(),
        justified_checkpoint,
        finalized_checkpoint,
        blocks,
        block_states,
        checkpoint_states);
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
    UnsignedLong justified_slot =
        compute_start_slot_of_epoch(store.getJustifiedCheckpoint().getEpoch());

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

  // Fork Choice Event Handlers

  /**
   * @param store
   * @param time
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_tick</a>
   */
  public static void on_tick(Store.Transaction store, UnsignedLong time) {
    store.setTime(time);
  }

  /**
   * @param store
   * @param block
   * @param st
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md#on_block</a>
   */
  public static BlockProcessingRecord on_block(
      Store.Transaction store, BeaconBlock block, StateTransition st)
      throws StateTransitionException {
    // Make a copy of the state to avoid mutability issues
    checkArgument(
        store.containsBlockState(block.getParent_root()),
        "on_block: Parent block state is not contained in block_state");
    final BeaconState preState = store.getBlockState(block.getParent_root());
    BeaconStateWithCache state = BeaconStateWithCache.deepCopy((BeaconStateWithCache) preState);

    // Blocks cannot be in the future. If they are, their consideration must be delayed until the
    // are in the past.
    checkArgument(
        store
                .getTime()
                .compareTo(
                    state
                        .getGenesis_time()
                        .plus(block.getSlot().times(UnsignedLong.valueOf(SECONDS_PER_SLOT))))
            >= 0,
        "on_block: Blocks cannot be in the future.");

    // Add new block to the store
    store.putBlock(block.signing_root("signature"), block);

    checkArgument(
        get_ancestor(
                store,
                block.signing_root("signature"),
                store.getBlock(store.getFinalizedCheckpoint().getRoot()).getSlot())
            .equals(store.getFinalizedCheckpoint().getRoot()),
        "on_block: Check block is a descendant of the finalized block");

    checkArgument(
        block
                .getSlot()
                .compareTo(compute_start_slot_of_epoch(store.getFinalizedCheckpoint().getEpoch()))
            > 0,
        "on_block: Check that block is later than the finalized epoch slot");

    // Check the block is valid and compute the post-state
    state = st.initiate(state, block, true);

    // Add new state for this block to the store
    store.putBlockState(block.signing_root("signature"), state);

    // Update justified checkpoint
    if (state
            .getCurrent_justified_checkpoint()
            .getEpoch()
            .compareTo(store.getJustifiedCheckpoint().getEpoch())
        > 0) {
      store.setJustifiedCheckpoint(state.getCurrent_justified_checkpoint());
    }

    // Update finalized checkpoint
    if (state
            .getFinalized_checkpoint()
            .getEpoch()
            .compareTo(store.getFinalizedCheckpoint().getEpoch())
        > 0) {
      store.setFinalizedCheckpoint(state.getFinalized_checkpoint());
    }
    return new BlockProcessingRecord(preState, block, state);
  }

  /**
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

    checkArgument(
        store.containsBlock(target.getRoot()),
        "on_attestation: Cannot calculate the current shuffling if have not seen the target");

    // Attestations cannot be from future epochs. If they are, delay consideration until the epoch
    // arrives
    BeaconStateWithCache base_state =
        new BeaconStateWithCache((BeaconStateWithCache) store.getBlockState(target.getRoot()));
    checkArgument(
        store
                .getTime()
                .compareTo(
                    base_state
                        .getGenesis_time()
                        .plus(
                            compute_start_slot_of_epoch(target.getEpoch())
                                .times(UnsignedLong.valueOf(SECONDS_PER_SLOT))))
            >= 0,
        "on_attestation: Attestations cannot be from the future epochs");

    // Store target checkpoint state if not yet seen
    if (!store.containsCheckpointState(target)) {
      stateTransition.process_slots(
          base_state, compute_start_slot_of_epoch(target.getEpoch()), false);
      store.putCheckpointState(target, base_state);
    }
    BeaconState target_state = store.getCheckpointState(target);

    // Attestations can only affect the fork choice of subsequent slots.
    // Delay consideration in the fork choice until their slot is in the past.
    UnsignedLong attestation_slot = get_attestation_data_slot(target_state, attestation.getData());
    checkArgument(
        store
                .getTime()
                .compareTo(
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
    List<UnsignedLong> all_indices = new ArrayList<>();
    all_indices.addAll(indexed_attestation.getCustody_bit_0_indices());
    all_indices.addAll(indexed_attestation.getCustody_bit_1_indices());
    for (UnsignedLong i : all_indices) {
      if (!store.containsLatestMessage(i)
          || target.getEpoch().compareTo(store.getLatestMessage(i).getEpoch()) > 0) {
        store.putLatestMessage(
            i, new LatestMessage(target.getEpoch(), attestation.getData().getBeacon_block_root()));
      }
    }
  }
}
