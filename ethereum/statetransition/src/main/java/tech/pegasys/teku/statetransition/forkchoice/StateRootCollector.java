/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class StateRootCollector {
  private static final Logger LOG = LogManager.getLogger();

  public static void addParentStateRoots(
      final BeaconState blockSlotState, final StoreTransaction transaction) {
    final UInt64 newBlockSlot = blockSlotState.getSlot();
    final SSZVector<Bytes32> blockRoots = blockSlotState.getBlock_roots();
    final SSZVector<Bytes32> stateRoots = blockSlotState.getState_roots();
    final UInt64 minimumSlot = newBlockSlot.minusMinZero(SLOTS_PER_HISTORICAL_ROOT);
    // Get the parent block root from the state as the genesis block root is recorded as 0
    final Bytes32 parentBlockRoot =
        blockRoots.get(newBlockSlot.minusMinZero(1).mod(SLOTS_PER_HISTORICAL_ROOT).intValue());
    UInt64 slot = newBlockSlot.minusMinZero(1);
    while (slot.isGreaterThanOrEqualTo(minimumSlot) && !slot.isZero()) {
      final Bytes32 previousBlockRoot = getValue(blockRoots, slot.minus(1));
      if (!previousBlockRoot.equals(parentBlockRoot)) {
        // Reached the first slot of the parent block - its state root is already be imported
        return;
      }
      transaction.putStateRoot(
          getValue(stateRoots, slot), new SlotAndBlockRoot(slot, parentBlockRoot));
      slot = slot.decrement();
    }
    if (!slot.isZero()) {
      LOG.warn("Missing some state root mappings prior to slot {}", minimumSlot);
    }
  }

  private static Bytes32 getValue(final SSZVector<Bytes32> roots, final UInt64 slot) {
    return roots.get(slot.mod(SLOTS_PER_HISTORICAL_ROOT).intValue());
  }
}
