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

import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.types.SlotNumber;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * State transition.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#beacon-chain-state-transition-function">Beacon
 *     chain state transition function</a> in the spec.
 */
public interface SpecStateTransition extends EpochProcessing, BlockProcessing {

  /*
   def process_slot(state: BeaconState) -> None:
     # Cache state root
     previous_state_root = hash_tree_root(state)
     state.latest_state_roots[state.slot % SLOTS_PER_HISTORICAL_ROOT] = previous_state_root

     # Cache latest block header state root
     if state.latest_block_header.state_root == ZERO_HASH:
         state.latest_block_header.state_root = previous_state_root

     # Cache block root
     previous_block_root = signing_root(state.latest_block_header)
     state.latest_block_roots[state.slot % SLOTS_PER_HISTORICAL_ROOT] = previous_block_root
  */
  default void process_slot(MutableBeaconState state) {
    // Cache state root
    Hash32 previous_state_root = hash_tree_root(state);
    state
        .getStateRoots()
        .update(
            state.getSlot().modulo(getConstants().getSlotsPerHistoricalRoot()),
            root -> previous_state_root);

    // Cache latest block header state root
    if (state.getLatestBlockHeader().getStateRoot().equals(Hash32.ZERO)) {
      state.setLatestBlockHeader(
          new BeaconBlockHeader(
              state.getLatestBlockHeader().getSlot(),
              state.getLatestBlockHeader().getParentRoot(),
              previous_state_root,
              state.getLatestBlockHeader().getBodyRoot(),
              state.getLatestBlockHeader().getSignature()));
    }

    // Cache block root
    Hash32 previous_block_root = signing_root(state.getLatestBlockHeader());
    state
        .getBlockRoots()
        .update(
            state.getSlot().modulo(getConstants().getSlotsPerHistoricalRoot()),
            root -> previous_block_root);
  }

  /*
   def process_slots(state: BeaconState, slot: Slot) -> None:
     assert state.slot <= slot
     while state.slot < slot:
         process_slot(state)
         # Process epoch on the first slot of the next epoch
         if (state.slot + 1) % SLOTS_PER_EPOCH == 0:
             process_epoch(state)
         state.slot += 1
  */
  default void process_slots(MutableBeaconState state, SlotNumber slot) {
    assertTrue(state.getSlot().lessEqual(slot));
    while (state.getSlot().less(slot)) {
      process_slot(state);
      // Process epoch on the first slot of the next epoch
      if (state
          .getSlot()
          .increment()
          .modulo(getConstants().getSlotsPerEpoch())
          .equals(SlotNumber.ZERO)) {
        process_epoch(state);
      }
      state.setSlot(state.getSlot().increment());
    }
  }

  /*
   def state_transition(state: BeaconState, block: BeaconBlock, validate_state_root: bool=False) -> BeaconState:
     # Process slots (including those with no blocks) since block
     process_slots(state, block.slot)
     # Process block
     process_block(state, block)
     # Validate state root (`validate_state_root == True` in production)
     if validate_state_root:
         assert block.state_root == hash_tree_root(state)
     # Return post-state
     return state
  */
  default MutableBeaconState state_transition(
      MutableBeaconState state, BeaconBlock block, boolean validate_state_root) {
    // Process slots (including those with no blocks) since block
    process_slots(state, block.getSlot());
    // Process block
    process_block(state, block);
    // Validate state root (`validate_state_root == True` in production)
    if (validate_state_root) {
      assertTrue(block.getStateRoot().equals(hash_tree_root(state)));
    }
    // Return post-state
    return state;
  }
}
