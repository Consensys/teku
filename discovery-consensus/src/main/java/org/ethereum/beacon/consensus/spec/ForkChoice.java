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

package org.ethereum.beacon.consensus.spec;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * Fork choice rule.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_fork-choice.md">Beacon
 *     Chain Fork Choice</a> in the spec.
 */
public interface ForkChoice extends HelperFunction {

  /*
   def get_ancestor(store: Store, root: Hash, slot: Slot) -> Hash:
     block = store.blocks[root]
     if block.slot > slot:
         return get_ancestor(store, block.parent_root, slot)
     elif block.slot == slot:
         return root
     else:
         return Bytes32()  # root is older than queried slot: no results.
  */
  default Optional<Hash32> get_ancestor(Store store, Hash32 root, SlotNumber slot) {
    Optional<BeaconBlock> aBlock = store.getBlock(root);
    if (!aBlock.isPresent()) {
      return Optional.empty();
    }

    BeaconBlock block = aBlock.get();
    if (block.getSlot().greater(slot)) {
      return get_ancestor(store, block.getParentRoot(), slot);
    } else if (block.getSlot().equals(slot)) {
      return Optional.of(root);
    } else {
      return Optional.of(Hash32.ZERO);
    }
  }

  /*
   def get_latest_attesting_balance(store: Store, root: Hash) -> Gwei:
     state = store.checkpoint_states[store.justified_checkpoint]
     active_indices = get_active_validator_indices(state, get_current_epoch(state))
     return Gwei(sum(
         state.validators[i].effective_balance for i in active_indices
         if (i in store.latest_messages
             and get_ancestor(store, store.latest_messages[i].root, store.blocks[root].slot) == root)
     ))
  */
  default Gwei get_latest_attesting_balance(Store store, Hash32 root) {
    Optional<BeaconState> state = store.getCheckpointState(store.getJustifiedCheckpoint());
    if (!state.isPresent()) {
      return Gwei.ZERO;
    }

    List<ValidatorIndex> active_indices =
        get_active_validator_indices(state.get(), get_current_epoch(state.get()));

    return active_indices.stream()
        .filter(
            i -> {
              Optional<LatestMessage> latest_message = store.getLatestMessage(i);
              Optional<BeaconBlock> block = store.getBlock(root);

              if (!latest_message.isPresent() || !block.isPresent()) {
                return false;
              }

              Optional<Hash32> ancestor =
                  get_ancestor(store, latest_message.get().root, block.get().getSlot());

              return ancestor.map(hash32 -> hash32.equals(root)).orElse(false);
            })
        .map(i -> state.get().getValidators().get(i).getEffectiveBalance())
        .reduce(Gwei.ZERO, Gwei::plus);
  }

  /*
   def get_head(store: Store) -> Hash:
     # Execute the LMD-GHOST fork choice
     head = store.justified_checkpoint.root
     justified_slot = compute_start_slot_of_epoch(store.justified_checkpoint.epoch)
     while True:
         children = [
             root for root in store.blocks.keys()
             if store.blocks[root].parent_root == head and store.blocks[root].slot > justified_slot
         ]
         if len(children) == 0:
             return head
         # Sort by latest attesting balance with ties broken lexicographically
         head = max(children, key=lambda root: (get_latest_attesting_balance(store, root), root))
  */
  default Hash32 get_head(Store store) {
    // Execute the LMD-GHOST fork choice
    Hash32 head = store.getJustifiedCheckpoint().getRoot();
    while (true) {
      List<Hash32> children = store.getChildren(head);
      if (children.isEmpty()) {
        return head;
      }

      head =
          children.stream()
              .max(Comparator.comparing(root -> get_latest_attesting_balance(store, root)))
              .get();
    }
  }

  class LatestMessage {
    private final EpochNumber epoch;
    private final Hash32 root;

    public LatestMessage(EpochNumber epoch, Hash32 root) {
      this.epoch = epoch;
      this.root = root;
    }

    public EpochNumber getEpoch() {
      return epoch;
    }

    public Hash32 getRoot() {
      return root;
    }
  }

  interface Store {
    Checkpoint getJustifiedCheckpoint();

    Checkpoint getFinalizedCheckpoint();

    Optional<BeaconBlock> getBlock(Hash32 root);

    Optional<BeaconState> getState(Hash32 root);

    default Optional<BeaconState> getCheckpointState(Checkpoint checkpoint) {
      Optional<BeaconBlock> block = getBlock(checkpoint.getRoot());
      Optional<Hash32> root = block.flatMap(b -> Optional.of(b.getStateRoot()));
      return root.flatMap(this::getState);
    }

    Optional<LatestMessage> getLatestMessage(ValidatorIndex index);

    List<Hash32> getChildren(Hash32 root);
  }
}
