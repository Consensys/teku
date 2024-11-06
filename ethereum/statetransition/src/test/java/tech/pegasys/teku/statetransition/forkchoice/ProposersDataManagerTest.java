/*
 * Copyright Consensys Software Inc., 2023
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
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

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ProposersDataManager manager;
  private Eth1Address defaultAddress;
  private UInt64 currentForkFirstSlot;

  @BeforeEach
  public void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec =
        switch (specContext.getSpecMilestone()) {
          case PHASE0 -> TestSpecFactory.createMinimalPhase0();
          case ALTAIR -> TestSpecFactory.createMinimalWithAltairForkEpoch(currentForkEpoch);
          case BELLATRIX -> TestSpecFactory.createMinimalWithBellatrixForkEpoch(currentForkEpoch);
          case CAPELLA -> TestSpecFactory.createMinimalWithCapellaForkEpoch(currentForkEpoch);
          case DENEB -> TestSpecFactory.createMinimalWithDenebForkEpoch(currentForkEpoch);
          case ELECTRA -> TestSpecFactory.createMinimalWithElectraForkEpoch(currentForkEpoch);
        };
    currentForkFirstSlot = spec.computeStartSlotAtEpoch(currentForkEpoch);
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
  }

  @TestTemplate
  void validatorIsConnected_notFound_withEmptyPreparedList() {
    assertThat(manager.validatorIsConnected(UInt64.ZERO, UInt64.ZERO)).isFalse();
  }

  @TestTemplate
  void validatorIsConnected_found_withPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.validatorIsConnected(UInt64.ONE, UInt64.valueOf(1))).isTrue();
  }

  @TestTemplate
  void validatorIsConnected_notFound_withDifferentPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.validatorIsConnected(UInt64.valueOf(2), UInt64.valueOf(2))).isFalse();
  }

  @TestTemplate
  void validatorIsConnected_notFound_withExpiredPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.validatorIsConnected(UInt64.ONE, UInt64.valueOf(26))).isFalse();
  }

  @TestTemplate
  void shouldSetMaxAndTargetBlobCount() throws ExecutionException, InterruptedException {
    final Spec specMock = mock(Spec.class);
    when(specMock.computeEpochAtSlot(any())).thenReturn(currentForkEpoch);
    final UInt64 timestamp = dataStructureUtil.randomUInt64();
    when(specMock.computeTimeAtSlot(any(), any())).thenReturn(timestamp);
    final Bytes32 random = dataStructureUtil.randomBytes32();
    when(specMock.getRandaoMix(any(), any())).thenReturn(random);
    when(specMock.getMaxBlobsPerBlock(currentForkFirstSlot)).thenReturn(spec.getMaxBlobsPerBlock());
    when(specMock.getTargetBlobsPerBlock(currentForkFirstSlot))
        .thenReturn(spec.getTargetBlobsPerBlock(currentForkFirstSlot));
    manager =
        new ProposersDataManager(
            mock(EventThread.class),
            specMock,
            metricsSystem,
            channel,
            recentChainData,
            Optional.of(defaultAddress),
            true);
    final ForkChoiceUpdateData forkChoiceUpdateDataMock = mock(ForkChoiceUpdateData.class);
    when(forkChoiceUpdateDataMock.hasHeadBlockHash()).thenReturn(true);
    final ForkChoiceState forkChoiceStateMock = mock(ForkChoiceState.class);
    when(forkChoiceStateMock.getHeadBlockRoot()).thenReturn(dataStructureUtil.randomBytes32());
    when(forkChoiceUpdateDataMock.getForkChoiceState()).thenReturn(forkChoiceStateMock);
    when(recentChainData.isJustifiedCheckpointFullyValidated()).thenReturn(true);
    final ChainHead chainHeadMock = mock(ChainHead.class);
    when(chainHeadMock.getSlot()).thenReturn(UInt64.ZERO);
    when(chainHeadMock.getRoot()).thenReturn(dataStructureUtil.randomBytes32());
    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHeadMock));
    final BeaconState beaconStateMock = mock(BeaconState.class);
    when(chainHeadMock.getState()).thenReturn(SafeFuture.completedFuture(beaconStateMock));

    final SafeFuture<Optional<PayloadBuildingAttributes>> payloadBuildingAttributesFuture =
        manager.calculatePayloadBuildingAttributes(
            currentForkFirstSlot, true, forkChoiceUpdateDataMock, false);
    final Optional<PayloadBuildingAttributes> maybePayloadBuildingAttributes =
        payloadBuildingAttributesFuture.get();
    assertThat(maybePayloadBuildingAttributes).isPresent();
    assertThat(maybePayloadBuildingAttributes.get().getTargetBlobCount())
        .isEqualTo(spec.getTargetBlobsPerBlock(currentForkFirstSlot).map(UInt64::valueOf));
    assertThat(maybePayloadBuildingAttributes.get().getMaximumBlobCount())
        .isEqualTo(spec.getMaxBlobsPerBlock(currentForkFirstSlot).map(UInt64::valueOf));
  }
}
