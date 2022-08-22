/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.StateTransition;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class BuilderCircuitBreakerImplTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();

  private final StateTransition stateTransition = new StateTransition(spec::atSlot);

  private final BuilderCircuitBreakerImpl builderCircuitBreaker =
      new BuilderCircuitBreakerImpl(spec, INSPECTION_WINDOW, ALLOWED_FAULTS);

  private static final int INSPECTION_WINDOW = 10;
  private static final int ALLOWED_FAULTS = 5;

  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  protected ChainUpdater chainUpdater;

  @BeforeAll
  public static void disableDepositBlsVerification() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void enableDepositBlsVerification() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @Test
  @BeforeEach
  void setUp() {
    // all tests assume 64 block roots history
    assertThat(spec.getGenesisSpec().getSlotsPerHistoricalRoot()).isEqualTo(64);

    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    chainUpdater = storageSystem.chainUpdater();
    storageSystem.chainUpdater().initializeGenesis(false);
    recentChainData = storageSystem.recentChainData();
  }

  @Test
  void shouldNotEngage_noMissedSlots() {
    final UInt64 blockBuildingSlot = UInt64.valueOf(32);

    final BeaconState preState = advance(31, false);

    final BeaconState state = preState.updated(s -> s.setSlot(blockBuildingSlot));

    assertThat(builderCircuitBreaker.isEngaged(state)).isFalse();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(10);
  }

  @Test
  void shouldNotEngage_minimalAllowedFaults()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // no missing slot up to 61 (unique count: 3)
    advance(61, false);

    // 62, 63 and 64 missing, 65 with block (unique count: 3 + 1)
    advance(65, true);

    // 66, 67 missing, 68 with block (unique count: 3 + 1 + 1)
    final BeaconState preState = advance(68, true);

    // generate state for the building slot
    final BeaconState state = stateTransition.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isFalse();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(5);
  }

  @Test
  void shouldEngage_belowAllowedFaultsWithLastSlotNotEmpty()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // no missing slot up to 61 (unique count: 3)
    advance(61, false);

    // 62, 63, 64, 65 66, 67 missing, 68 with block (unique count: 3 + 1)
    final BeaconState preState = advance(68, true);

    // generate state for the building slot
    final BeaconState state = stateTransition.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(4);
  }

  @Test
  void shouldEngage_belowAllowedFaultsWithLastSlotEmpty()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // no missing slot up to 61 (unique count: 3)
    advance(61, false);

    // 62, 63, 64, 65 66, 67 with block (unique count: 3 + 1)
    final BeaconState preState = advance(67, true);

    // generate state for the building slot
    // last block header will be from slot 67
    final BeaconState state = stateTransition.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(4);
  }

  @Test
  void shouldEngage_belowAllowedFaults_lastBlockHeaderWellOff()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // build up to 58
    final BeaconState preState = advance(58, false);

    final BeaconState state = stateTransition.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertThat(builderCircuitBreaker.getLatestUniqueBlockRootsCount(state)).isEqualTo(0);
  }

  private BeaconState advance(final long toSlot, final boolean missing) {
    if (missing) {
      SignedBlockAndState withBlocks = chainUpdater.advanceChain(toSlot);
      chainUpdater.updateBestBlock(withBlocks);
    } else {
      SignedBlockAndState withBlocks = chainUpdater.advanceChainUntil(toSlot);
      chainUpdater.updateBestBlock(withBlocks);
    }
    return recentChainData.getBestState().orElseThrow().getImmediately();
  }
}
