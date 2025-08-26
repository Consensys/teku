/*
 * Copyright Consensys Software Inc., 2025
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(allMilestones = true)
class ProposersDataManagerTest {

  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ExecutionLayerChannel channel = ExecutionLayerChannel.NOOP;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private List<BeaconPreparableProposer> proposers;

  private final ChainHead chainHead = mock(ChainHead.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ProposersDataManager manager;
  private Eth1Address defaultAddress;

  @BeforeEach
  public void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec =
        spy(
            switch (specContext.getSpecMilestone()) {
              case PHASE0 -> TestSpecFactory.createMinimalPhase0();
              case ALTAIR -> TestSpecFactory.createMinimalWithAltairForkEpoch(currentForkEpoch);
              case BELLATRIX ->
                  TestSpecFactory.createMinimalWithBellatrixForkEpoch(currentForkEpoch);
              case CAPELLA -> TestSpecFactory.createMinimalWithCapellaForkEpoch(currentForkEpoch);
              case DENEB -> TestSpecFactory.createMinimalWithDenebForkEpoch(currentForkEpoch);
              case ELECTRA -> TestSpecFactory.createMinimalWithElectraForkEpoch(currentForkEpoch);
              case FULU -> TestSpecFactory.createMinimalWithFuluForkEpoch(currentForkEpoch);
              case GLOAS -> TestSpecFactory.createMinimalWithGloasForkEpoch(currentForkEpoch);
            });
    dataStructureUtil = specContext.getDataStructureUtil();
    defaultAddress = dataStructureUtil.randomEth1Address();
    manager =
        new ProposersDataManager(
            mock(EventThread.class),
            spec,
            metricsSystem,
            channel,
            recentChainData,
            Optional.of(defaultAddress),
            false);
    proposers =
        List.of(
            new BeaconPreparableProposer(UInt64.ONE, dataStructureUtil.randomEth1Address()),
            new BeaconPreparableProposer(UInt64.ZERO, defaultAddress));

    when(chainHead.getSlot()).thenReturn(UInt64.ONE);
    when(chainHead.getState()).thenReturn(SafeFuture.completedFuture(mock(BeaconState.class)));

    when(recentChainData.retrieveStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(mock(BeaconState.class))));
    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));
  }

  @TestTemplate
  void isValidatorConnected_notFound_withEmptyPreparedList() {
    assertThat(manager.isValidatorConnected(0, UInt64.ZERO)).isFalse();
  }

  @TestTemplate
  void isValidatorConnected_found_withPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.isValidatorConnected(1, UInt64.valueOf(1))).isTrue();
  }

  @TestTemplate
  void isValidatorConnected_notFound_withDifferentPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.isValidatorConnected(2, UInt64.valueOf(2))).isFalse();
  }

  @TestTemplate
  void isValidatorConnected_notFound_withExpiredPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.isValidatorConnected(1, UInt64.valueOf(26))).isFalse();
  }

  @TestTemplate
  void isBlockProposerConnected_notFound_currentEpoch() {
    doReturn(2).when(spec).getBeaconProposerIndex(any(), any());
    manager.updatePreparedProposers(proposers, UInt64.ONE);

    assertThat(manager.isBlockProposerConnected(UInt64.ONE)).isCompletedWithValue(false);
    verify(recentChainData, never()).retrieveStateAtSlot(any());
  }

  @TestTemplate
  void isBlockProposerConnected_found_currentEpoch() {
    doReturn(1).when(spec).getBeaconProposerIndex(any(), any());
    manager.updatePreparedProposers(proposers, UInt64.ONE);

    assertThat(manager.isBlockProposerConnected(UInt64.ONE)).isCompletedWithValue(true);
    verify(recentChainData, never()).retrieveStateAtSlot(any());
  }

  @TestTemplate
  void isBlockProposerConnected_notFound_nextEpoch() {
    doReturn(2).when(spec).getBeaconProposerIndex(any(), any());
    manager.updatePreparedProposers(proposers, UInt64.ONE);

    assertThat(manager.isBlockProposerConnected(UInt64.valueOf(10))).isCompletedWithValue(false);
    verify(recentChainData).retrieveStateAtSlot(any());
  }

  @TestTemplate
  void isBlockProposerConnected_found_nextEpoch() {
    doReturn(1).when(spec).getBeaconProposerIndex(any(), any());
    manager.updatePreparedProposers(proposers, UInt64.ONE);

    assertThat(manager.isBlockProposerConnected(UInt64.valueOf(10))).isCompletedWithValue(true);
    verify(recentChainData).retrieveStateAtSlot(any());
  }
}
