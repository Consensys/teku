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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.fulu.StatusMessageFulu;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

class StatusMessageFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private StatusMessageFactory statusMessageFactory;

  @BeforeEach
  public void setUp() {
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(UInt64.ZERO);
    when(recentChainData.getCurrentForkDigest())
        .thenReturn(Optional.of(dataStructureUtil.randomBytes4()));
    when(recentChainData.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint()));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    when(recentChainData.getChainHead())
        .thenAnswer(__ -> Optional.of(ChainHead.create(dataStructureUtil.randomBlockAndState(0))));

    statusMessageFactory = new StatusMessageFactory(spec, combinedChainDataClient, metricsSystem);
  }

  @Test
  public void createStatusMessageShouldReturnRpcBodySelectorForAllSupportedVersions() {
    final RpcRequestBodySelector<StatusMessage> rpcRequestBodySelector =
        statusMessageFactory.createStatusMessage().orElseThrow();

    assertThat(rpcRequestBodySelector.getBody().apply("/eth2/beacon_chain/req/status/1/ssz_snappy"))
        .hasValueSatisfying(
            statusMessage -> assertThat(statusMessage).isInstanceOf(StatusMessagePhase0.class));
    assertThat(rpcRequestBodySelector.getBody().apply("/eth2/beacon_chain/req/status/2/ssz_snappy"))
        .hasValueSatisfying(
            statusMessage -> assertThat(statusMessage).isInstanceOf(StatusMessageFulu.class));
  }

  @Test
  public void createStatusMessageShouldReturnRpcBodySelectorThatFailsForUnsupportedVersion() {
    final RpcRequestBodySelector<StatusMessage> rpcRequestBodySelector =
        statusMessageFactory.createStatusMessage().orElseThrow();

    assertThatThrownBy(
            () ->
                rpcRequestBodySelector
                    .getBody()
                    .apply("/eth2/beacon_chain/req/status/3/ssz_snappy"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected protocol version: 3");
  }

  @Test
  public void shouldOnlyUpdateEarliestSlotAvailableAtBeginningOfEpoch() {

    when(combinedChainDataClient.getEarliestAvailableBlockSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(UInt64.ZERO)));

    final UInt64 currentEpoch = UInt64.ONE;
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(currentEpoch);

    final UInt64 epoch1StartSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    statusMessageFactory.onSlot(epoch1StartSlot);
    statusMessageFactory.onSlot(epoch1StartSlot.plus(UInt64.ONE));

    final UInt64 nextEpoch = currentEpoch.plus(UInt64.ONE);
    when(combinedChainDataClient.getCurrentEpoch()).thenReturn(nextEpoch);

    final UInt64 epoch2StartSlot = spec.computeStartSlotAtEpoch(nextEpoch);
    statusMessageFactory.onSlot(epoch2StartSlot);
    statusMessageFactory.onSlot(epoch2StartSlot.plus(UInt64.ONE));

    verify(combinedChainDataClient, times(2)).getEarliestAvailableBlockSlot();
  }

  @Test
  public void shouldUseFinalizedSlotWhenEarliestSlotAvailableIsEmpty() {
    final RpcRequestBodySelector<StatusMessage> rpcRequestBodySelector =
        statusMessageFactory.createStatusMessage().orElseThrow();

    final UInt64 currentEpoch = UInt64.valueOf(99);
    when(recentChainData.getFinalizedEpoch()).thenReturn(currentEpoch);
    final UInt64 expectedSlot = spec.computeStartSlotAtEpoch(currentEpoch);

    checkStatusMessagedHasExpectedEarliestAvailableSlot(rpcRequestBodySelector, expectedSlot);
  }

  @Test
  public void shouldUpdatedEarliestAvailableSlot() {
    final RpcRequestBodySelector<StatusMessage> rpcRequestBodySelector =
        statusMessageFactory.createStatusMessage().orElseThrow();

    tickOnSlotAndUpdatedEarliestSlotAvailable(UInt64.valueOf(20));
    checkStatusMessagedHasExpectedEarliestAvailableSlot(rpcRequestBodySelector, UInt64.valueOf(20));

    tickOnSlotAndUpdatedEarliestSlotAvailable(UInt64.valueOf(10));
    checkStatusMessagedHasExpectedEarliestAvailableSlot(rpcRequestBodySelector, UInt64.valueOf(10));
  }

  private void tickOnSlotAndUpdatedEarliestSlotAvailable(final UInt64 earliestAvailableSlot) {
    when(combinedChainDataClient.getEarliestAvailableBlockSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(earliestAvailableSlot)));
    statusMessageFactory.onSlot(UInt64.ZERO);
  }

  private static void checkStatusMessagedHasExpectedEarliestAvailableSlot(
      final RpcRequestBodySelector<StatusMessage> rpcRequestBodySelector, final UInt64 value) {
    assertThat(rpcRequestBodySelector.getBody().apply("/eth2/beacon_chain/req/status/2/ssz_snappy"))
        .hasValueSatisfying(
            statusMessage -> assertThat(statusMessage.getEarliestAvailableSlot()).hasValue(value));
  }
}
