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

package tech.pegasys.artemis.statetransition;

import static java.util.Objects.requireNonNull;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class LmdGhost {

  /**
   * Execute the LMD-GHOST algorithm to find the head ``BeaconBlock``.
   *
   * @param store
   * @param start_state
   * @param start_block
   * @return
   */
  public static BeaconBlock lmd_ghost(
      ChainStorageClient store, BeaconState start_state, BeaconBlock start_block) {
    List<Integer> active_validator_indices =
        ValidatorsUtil.get_active_validator_indices(
            start_state, compute_epoch_of_slot(start_state.getSlot()));

    List<MutablePair<Integer, BeaconBlock>> attestation_targets = new ArrayList<>();
    for (Integer validator_index : active_validator_indices) {
      if (get_latest_attestation_target(store, validator_index).isPresent()) {
        attestation_targets.add(
            new MutablePair<>(
                validator_index, get_latest_attestation_target(store, validator_index).get()));
      }
    }

    BeaconBlock head = start_block;
    List<BeaconBlock> children;
    while (true) {
      children = get_children(store, head);

      if (children.size() == 0) {
        return head;
      }

      UnsignedLong max_value = UnsignedLong.ZERO;
      for (BeaconBlock child : children) {
        UnsignedLong curr_value = get_vote_count(start_state, store, child, attestation_targets);
        if (curr_value.compareTo(max_value) > 0) {
          max_value = curr_value;
        }
      }

      final UnsignedLong max = max_value;

      head =
          children.stream()
              .filter(
                  child ->
                      get_vote_count(start_state, store, child, attestation_targets).compareTo(max)
                          == 0)
              .max(Comparator.comparing(child -> child.hash_tree_root().toHexString()))
              .get();
    }
  }

  /**
   * This function is defined inside lmd_ghost in spec. It is defined here separately for
   * legibility.
   *
   * @param start_state
   * @param store
   * @param block
   * @param attestation_targets
   * @return
   */
  public static UnsignedLong get_vote_count(
      BeaconState start_state,
      ChainStorageClient store,
      BeaconBlock block,
      List<MutablePair<Integer, BeaconBlock>> attestation_targets) {
    UnsignedLong sum = UnsignedLong.ZERO;
    for (MutablePair<Integer, BeaconBlock> index_target : attestation_targets) {
      int validator_index = index_target.getLeft();
      BeaconBlock target = index_target.getRight();

      Optional<BeaconBlock> ancestor = get_ancestor(store, target, block.getSlot());
      if (!ancestor.isPresent()) continue;
      if (ancestor.get().equals(block)) {
        sum =
            sum.plus(
                start_state.getValidator_registry().get(validator_index).getEffective_balance());
      }
    }
    return sum;
  }

  /**
   * Returns the child blocks of the given block
   *
   * @param store
   * @param block
   * @return
   */
  // TODO: OPTIMIZE: currently goes through all the values in processedBlockLookup
  public static List<BeaconBlock> get_children(ChainStorageClient store, BeaconBlock block) {
    List<BeaconBlock> children = new ArrayList<>();
    for (Map.Entry<Bytes32, BeaconBlock> entry : store.getProcessedBlockLookup().entrySet()) {
      BeaconBlock potential_child = entry.getValue();
      if (store.getParent(potential_child).isPresent()
          && store.getParent(potential_child).get().equals(block)) {
        children.add(potential_child);
      }
    }
    return children;
  }

  /**
   * Returns the target block in the attestation get_latest_attestation(store, validator).
   *
   * @param store
   * @param validatorIndex
   * @return
   */
  public static Optional<BeaconBlock> get_latest_attestation_target(
      ChainStorageClient store, int validatorIndex) {
    Optional<Attestation> latest_attestation = get_latest_attestation(store, validatorIndex);
    if (latest_attestation.isPresent()) {
      Optional<BeaconBlock> latest_attestation_target =
          store.getProcessedBlock(latest_attestation.get().getData().getBeacon_block_root());
      return latest_attestation_target;
    } else {
      return Optional.empty();
    }
  }

  /**
   * Returns the attestation with the highest slot number in store from validator. If several such
   * attestations exist, use the one the validator v observed first.
   *
   * @param store
   * @param validatorIndex
   * @return
   */
  public static Optional<Attestation> get_latest_attestation(
      ChainStorageClient store, int validatorIndex) {
    Optional<Attestation> latestAttestation = store.getLatestAttestation(validatorIndex);
    return latestAttestation;
  }

  /**
   * Get the ancestor of ``block`` with slot number ``slot``; return ``None`` if not found.
   *
   * @param store
   * @param block
   * @param slot
   * @return
   */
  public static Optional<BeaconBlock> get_ancestor(
      ChainStorageClient store, BeaconBlock block, UnsignedLong slot) {
    requireNonNull(block);
    UnsignedLong blockSlot = block.getSlot();
    if (blockSlot.compareTo(slot) == 0) {
      return Optional.of(block);
    } else if (blockSlot.compareTo(slot) < 0) {
      return Optional.ofNullable(null);
    } else {
      return get_ancestor(store, store.getParent(block).get(), slot);
    }
  }
}
