/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.networking.eth2.gossip.forks.versions;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.SignedBlsToExecutionChangeGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

@SuppressWarnings({"unchecked", "rawtypes"})
class GossipForkSubscriptionsCapellaTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();
  final Fork fork = spec.getForkSchedule().getFork(UInt64.ZERO);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final P2PConfig p2pConfig = mock(P2PConfig.class);

  private GossipForkSubscriptionsCapella gossipForkSubscriptions;

  @Test
  public void shouldAddBlsToExecutionChangesGossipManagerIfEnabled() {
    when(p2pConfig.isBlsToExecutionChangesSubnetEnabled()).thenReturn(true);
    gossipForkSubscriptions = spy(createGossipForkSubscriptionCapella());

    gossipForkSubscriptions.addSignedBlsToExecutionChangeGossipManager(forkInfo());

    assertThat(gossipForkSubscriptions.getSignedBlsToExecutionChangeGossipManager()).isNotEmpty();
    verify(gossipForkSubscriptions, times(1))
        .addGossipManager(any(SignedBlsToExecutionChangeGossipManager.class));
  }

  @Test
  public void shouldNotAddBlsToExecutionChangesGossipManagerIfDisabled() {
    when(p2pConfig.isBlsToExecutionChangesSubnetEnabled()).thenReturn(false);
    gossipForkSubscriptions = spy(createGossipForkSubscriptionCapella());

    gossipForkSubscriptions.addSignedBlsToExecutionChangeGossipManager(forkInfo());

    assertThat(gossipForkSubscriptions.getSignedBlsToExecutionChangeGossipManager()).isEmpty();
    verify(gossipForkSubscriptions, times(0)).addGossipManager(any());
  }

  @Test
  public void shouldPublishBlsToExecutionChangeMessageIfGossipManagerIsPresent() {
    gossipForkSubscriptions = createGossipForkSubscriptionCapella();

    // the actual gossip manager is created within the class, we are injecting a mock here to test
    // the isPresent() logic
    final SignedBlsToExecutionChangeGossipManager blsGossipManager =
        mock(SignedBlsToExecutionChangeGossipManager.class);
    gossipForkSubscriptions.setSignedBlsToExecutionChangeGossipManager(
        Optional.of(blsGossipManager));

    final SignedBlsToExecutionChange message = dataStructureUtil.randomSignedBlsToExecutionChange();
    gossipForkSubscriptions.publishSignedBlsToExecutionChangeMessage(message);

    verify(blsGossipManager).publish(eq(message));
  }

  private ForkInfo forkInfo() {
    return new ForkInfo(fork, dataStructureUtil.randomBytes32());
  }

  private GossipForkSubscriptionsCapella createGossipForkSubscriptionCapella() {
    final DiscoveryNetwork discoveryNetwork = mock(DiscoveryNetwork.class);
    final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(spec);
    final GossipEncoding gossipEncoding = mock(GossipEncoding.class);
    final OperationProcessor noopOperationProcessor = OperationProcessor.NOOP;

    return new GossipForkSubscriptionsCapella(
        fork,
        spec,
        p2pConfig,
        new StubAsyncRunner(),
        new StubMetricsSystem(),
        discoveryNetwork,
        recentChainData,
        gossipEncoding,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor);
  }
}
