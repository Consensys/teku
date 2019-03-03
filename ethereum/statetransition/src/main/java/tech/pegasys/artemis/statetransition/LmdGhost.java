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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.consensys.cava.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class LmdGhost {

  public static BeaconBlock lmd_ghost(
      ChainStorageClient store, BeaconState start_state, BeaconBlock start_block)
      throws StateTransitionException {
    List<Integer> active_validator_indices =
        ValidatorsUtil.get_active_validator_indices(
            start_state.getValidator_registry(),
            BeaconStateUtil.slot_to_epoch(UnsignedLong.valueOf(start_block.getSlot())));

    List<BeaconBlock> attestation_targets = new ArrayList<>();
    for (Integer validatorIndex : active_validator_indices) {
      if (get_latest_attestation_target(store, validatorIndex).isPresent()) {
        attestation_targets.add(get_latest_attestation_target(store, validatorIndex).get());
      }
    }

    BeaconBlock head = start_block;
    List<BeaconBlock> children;
    while (true) {
      children = get_children(store, head);

      if (children.size() == 0) {
        return head;
      }

      head =
          children.stream()
              .max(
                  Comparator.comparing(
                      child_block ->
                          Math.toIntExact(
                              get_vote_count(store, child_block, attestation_targets).longValue())))
              .get();
    }
  }

  /*
   * This function is defined inside lmd_ghost in spec. It is defined here separately for legibility.
   */
  public static UnsignedLong get_vote_count(
      ChainStorageClient store, BeaconBlock block, List<BeaconBlock> attestation_targets) {
    UnsignedLong vote_count = UnsignedLong.ZERO;
    for (BeaconBlock target : attestation_targets) {
      Optional<BeaconBlock> ancestor =
          get_ancestor(store, target, UnsignedLong.valueOf(block.getSlot()));
      if (!ancestor.isPresent()) continue;
      if (ancestor.get().equals(block)) {
        vote_count = vote_count.plus(UnsignedLong.ONE);
      }
    }
    return vote_count;
  }

  /*
   * Spec pseudo-code:
   *  Let get_children(store: Store, block: BeaconBlock) -> List[BeaconBlock] returns
   *  the child blocks of the given block.
   */
  // TODO: OPTIMIZE: currently goes through all the values in processedBlockLookup
  public static List<BeaconBlock> get_children(ChainStorageClient store, BeaconBlock block) {
    List<BeaconBlock> children = new ArrayList<>();
    for (Map.Entry<Bytes, BeaconBlock> entry : store.getProcessedBlockLookup().entrySet()) {
      BeaconBlock potential_child = entry.getValue();
      if (store.getParent(potential_child).isPresent()
          && store.getParent(potential_child).get().equals(block)) {
        children.add(potential_child);
      }
    }
    return children;
  }

  /*
   * Spec pseudo-code:
   *  Let get_latest_attestation_target(store: Store, validator: Validator) -> BeaconBlock
   *  be the target block in the attestation get_latest_attestation(store, validator).
   */
  public static Optional<BeaconBlock> get_latest_attestation_target(
      ChainStorageClient store, int validatorIndex) throws StateTransitionException {
    Optional<Attestation> latest_attestation = get_latest_attestation(store, validatorIndex);
    if (latest_attestation.isPresent()) {
      Optional<BeaconBlock> latest_attestation_target =
          store.getProcessedBlock(latest_attestation.get().getData().getBeacon_block_root());
      return latest_attestation_target;
    } else {
      return Optional.empty();
    }
  }

  /*
   * Spec pseudo-code:
   *  Let get_latest_attestation(store: Store, validator: Validator) -> Attestation
   *  be the attestation with the highest slot number in store from validator. If
   *  several such attestations exist, use the one the validator v observed first.
   */
  public static Optional<Attestation> get_latest_attestation(
      ChainStorageClient store, int validatorIndex) throws StateTransitionException {
    Optional<Attestation> latestAttestation = store.getLatestAttestation(validatorIndex);
    return latestAttestation;
  }

  /*
   * Spec pseudo-code:
   *  Let get_ancestor(store: Store, block: BeaconBlock, slot: SlotNumber) -> BeaconBlock
   *  be the ancestor of block with slot number slot. The get_ancestor function can be
   *  defined recursively as:
   */
  public static Optional<BeaconBlock> get_ancestor(
      ChainStorageClient store, BeaconBlock block, UnsignedLong slotNumber) {
    requireNonNull(block);
    UnsignedLong blockSlot = UnsignedLong.valueOf(block.getSlot());
    if (blockSlot.compareTo(slotNumber) == 0) {
      return Optional.of(block);
    } else if (blockSlot.compareTo(slotNumber) < 0) {
      return Optional.ofNullable(null);
    } else {
      return get_ancestor(store, store.getParent(block).get(), slotNumber);
    }
  }
}
