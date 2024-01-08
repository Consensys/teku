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
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class ProposersDataManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final ExecutionLayerChannel channel = ExecutionLayerChannel.NOOP;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  private final Eth1Address defaultAddress = dataStructureUtil.randomEth1Address();
  private final ProposersDataManager manager =
      new ProposersDataManager(
          mock(EventThread.class),
          spec,
          metricsSystem,
          channel,
          recentChainData,
          Optional.of(defaultAddress));

  final List<BeaconPreparableProposer> proposers =
      List.of(
          new BeaconPreparableProposer(UInt64.ONE, dataStructureUtil.randomEth1Address()),
          new BeaconPreparableProposer(UInt64.ZERO, defaultAddress));

  @Test
  void validatorIsConnected_notFound_withEmptyPreparedList() {
    assertThat(manager.validatorIsConnected(UInt64.ZERO, UInt64.ZERO)).isFalse();
  }

  @Test
  void validatorIsConnected_found_withPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.validatorIsConnected(UInt64.ONE, UInt64.valueOf(1))).isTrue();
  }

  @Test
  void validatorIsConnected_notFound_withDifferentPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.validatorIsConnected(UInt64.valueOf(2), UInt64.valueOf(2))).isFalse();
  }

  @Test
  void validatorIsConnected_notFound_withExpiredPreparedProposer() {
    manager.updatePreparedProposers(proposers, UInt64.ONE);
    assertThat(manager.validatorIsConnected(UInt64.ONE, UInt64.valueOf(26))).isFalse();
  }
}
