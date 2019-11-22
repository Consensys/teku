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
import static tech.pegasys.artemis.util.config.Constants.SAFE_SLOTS_TO_UPDATE_JUSTIFIED;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
import tech.pegasys.artemis.storage.ReadOnlyStore;
import tech.pegasys.artemis.storage.Store;

public class ForkChoiceUtil {

  public static UnsignedLong get_current_slot(Store.Transaction store, boolean useUnixTime) {
    UnsignedLong time = useUnixTime ? UnsignedLong.valueOf(Instant.now().getEpochSecond()) : store.getTime();
    return time
        .minus(store.getGenesisTime())
        .dividedBy(UnsignedLong.valueOf(SECONDS_PER_SLOT));
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
    UnsignedLong justified_slot =
        compute_start_slot_at_epoch(store.getJustifiedCheckpoint().getEpoch());

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
    if (new_justified_block
            .getSlot()
            .compareTo(compute_start_slot_at_epoch(store.getJustifiedCheckpoint().getEpoch()))
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
    if (!((current_slot.compareTo(previous_slot) > 0)
        && (compute_slots_since_epoch_start(current_slot).equals(UnsignedLong.ZERO)))) {
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
  public static BlockProcessingRecord on_block(
      Store.Transaction store, BeaconBlock block, StateTransition st)
      throws StateTransitionException {
    // Make a copy of the state to avoid mutability issues
    checkArgument(
        store.containsBlockState(block.getParent_root()),
        "on_block: Parent block state is not contained in block_state");
    final BeaconState preState = store.getBlockState(block.getParent_root());
    BeaconStateWithCache state =
        BeaconStateWithCache.deepCopy(BeaconStateWithCache.fromBeaconState(preState));

    // Blocks cannot be in the future. If they are, their consideration must be delayed until the
    // are in the past.
    UnsignedLong unixTimeStamp = UnsignedLong.valueOf(Instant.now().getEpochSecond());
    checkArgument(
        unixTimeStamp.compareTo(
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
                .compareTo(compute_start_slot_at_epoch(store.getFinalizedCheckpoint().getEpoch()))
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
      store.setBestJustifiedCheckpoint(state.getCurrent_justified_checkpoint());
      if (should_update_justified_checkpoint(store, state.getCurrent_justified_checkpoint())) {
        store.setJustifiedCheckpoint(state.getCurrent_justified_checkpoint());
      }
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
        BeaconStateWithCache.deepCopy((BeaconStateWithCache) store.getBlockState(target.getRoot()));
    UnsignedLong unixTimeStamp = UnsignedLong.valueOf(Instant.now().getEpochSecond());
    checkArgument(
        unixTimeStamp.compareTo(
                base_state
                    .getGenesis_time()
                    .plus(
                        compute_start_slot_at_epoch(target.getEpoch())
                            .times(UnsignedLong.valueOf(SECONDS_PER_SLOT))))
            >= 0,
        "on_attestation: Attestations cannot be from the future epochs");

    // Store target checkpoint state if not yet seen
    if (!store.containsCheckpointState(target)) {
      stateTransition.process_slots(
          base_state, compute_start_slot_at_epoch(target.getEpoch()), false);
      store.putCheckpointState(target, base_state);
    }
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
}
