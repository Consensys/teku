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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class StateRootCollectorTest {
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
  private final StoreTransaction transaction = mock(StoreTransaction.class);
  private SignedBlockAndState genesis;

  @BeforeEach
  void setUp() {
    genesis = storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  void shouldNotCaptureAnyStateRootsForGenesisState() {
    StateRootCollector.addParentStateRoots(genesis.getState(), transaction);
    verifyNoInteractions(transaction);
  }

  @Test
  void shouldNotCaptureAnyStateRootsForBlockInSlot1() {
    final BeaconState state = storageSystem.chainUpdater().advanceChain(1).getState();

    StateRootCollector.addParentStateRoots(state, transaction);
    verifyNoInteractions(transaction);
  }

  @Test
  void shouldCaptureRootsForEmptySlotsAfterGenesis() {
    final BeaconState state = storageSystem.chainUpdater().advanceChain(3).getState();

    StateRootCollector.addParentStateRoots(state, transaction);

    verifyStateRootRecorded(state, 2, genesis);
    verifyStateRootRecorded(state, 1, genesis);
    verifyNoMoreInteractions(transaction);
  }

  @Test
  void shouldNotCaptureStatesPriorToSlotsPerHistoricalRoot() throws Exception {
    final BeaconState state =
        storageSystem.chainUpdater().advanceChain(SLOTS_PER_HISTORICAL_ROOT + 2).getState();

    StateRootCollector.addParentStateRoots(state, transaction);

    final StateTransition stateTransition = new StateTransition();
    BeaconState historicState = genesis.getState();
    for (int i = 2; i < SLOTS_PER_HISTORICAL_ROOT + 2; i++) {
      final UInt64 slot = UInt64.valueOf(i);
      // Regenerate states to ensure we don't wrap around and record the wrong values.
      historicState = stateTransition.process_slots(historicState, slot);
      verify(transaction)
          .putStateRoot(
              historicState.hashTreeRoot(), new SlotAndBlockRoot(slot, genesis.getRoot()));
    }
    verifyNoMoreInteractions(transaction);
  }

  @Test
  void shouldStopRecordingStatesWhenParentBlockSlotReached() {
    final SignedBlockAndState parentBlock = storageSystem.chainUpdater().advanceChain(4);
    final BeaconState state = storageSystem.chainUpdater().advanceChain(7).getState();

    StateRootCollector.addParentStateRoots(state, transaction);
    verifyStateRootRecorded(state, 5, parentBlock);
    verifyStateRootRecorded(state, 6, parentBlock);
    verifyNoMoreInteractions(transaction);
  }

  private void verifyStateRootRecorded(
      final BeaconState state, final int slot, final BeaconBlockSummary parentBlock) {
    verify(transaction)
        .putStateRoot(
            getStateRoot(state, slot),
            new SlotAndBlockRoot(UInt64.valueOf(slot), parentBlock.getRoot()));
  }

  private Bytes32 getStateRoot(final BeaconState state, final int slot) {
    return state.getState_roots().get(slot % SLOTS_PER_HISTORICAL_ROOT);
  }
}
