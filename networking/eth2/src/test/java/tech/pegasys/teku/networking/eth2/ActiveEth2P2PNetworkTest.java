/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.config.GossipConfigurator;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ActiveEth2P2PNetworkTest {
  private final UInt64 altairForkEpoch = UInt64.valueOf(2);
  private final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(altairForkEpoch);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);

  // Stubs and mocks
  private final AsyncRunner asyncRunner = new StubAsyncRunner();
  private final DiscoveryNetwork<?> discoveryNetwork = mock(DiscoveryNetwork.class);
  private final Eth2PeerManager peerManager = mock(Eth2PeerManager.class);
  private final GossipForkManager gossipForkManager = mock(GossipForkManager.class);
  private final EventChannels eventChannels = mock(EventChannels.class);

  // Real dependencies
  private final SubnetSubscriptionService attestationSubnetService =
      new SubnetSubscriptionService();
  private final SubnetSubscriptionService syncCommitteeSubnetService =
      new SubnetSubscriptionService();
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final GossipConfigurator gossipConfigurator = GossipConfigurator.NOOP;
  private final Subscribers<ProcessedAttestationListener> subscribers = Subscribers.create(false);
  private final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider =
      subscribers::subscribe;

  private final ActiveEth2P2PNetwork network = createNetwork();
  private SignedBlockAndState genesis;
  private Fork phase0Fork;
  private Fork altairFork;
  private Bytes32 genesisValidatorsRoot;

  @BeforeEach
  public void setup() {
    when(discoveryNetwork.start()).thenReturn(SafeFuture.completedFuture(null));
    genesis = storageSystem.chainUpdater().initializeGenesis();
  }

  @Test
  public void start_setsGossipFork() {
    setupForkInfo();
    verify(discoveryNetwork, never()).setForkInfo(any(), any());
    assertThat(network.start()).isCompleted();

    final ForkInfo expectedFork =
        new ForkInfo(phase0Fork, genesis.getState().getGenesis_validators_root());
    verify(discoveryNetwork).setForkInfo(expectedFork, Optional.of(altairFork));
  }

  @Test
  public void onEpoch_shouldUpdateDiscoveryNetworkForkInfo() {
    setupForkInfo();
    // Start network
    verify(discoveryNetwork, never()).setForkInfo(any(), any());
    assertThat(network.start()).isCompleted();

    // Verify updates at startup
    verify(discoveryNetwork).start();
    ForkInfo expectedFork = new ForkInfo(phase0Fork, genesisValidatorsRoot);
    verify(discoveryNetwork).setForkInfo(expectedFork, Optional.of(altairFork));

    // Process epoch 1 - we shouldn't update fork info here
    network.onEpoch(UInt64.ONE);
    verify(discoveryNetwork).updateGossipTopicScoring(any());
    verifyNoMoreInteractions(discoveryNetwork);

    // At the altair upgrade epoch, we should update fork info
    network.onEpoch(altairForkEpoch);
    expectedFork = new ForkInfo(altairFork, genesisValidatorsRoot);
    verify(discoveryNetwork).setForkInfo(expectedFork, Optional.empty());

    // Processing altair again shouldn't cause any updates
    network.onEpoch(altairForkEpoch);
    verifyNoMoreInteractions(discoveryNetwork);

    // Next epoch should not update fork info
    network.onEpoch(altairForkEpoch.plus(1));
    verifyNoMoreInteractions(discoveryNetwork);

    // Reprocessing prior epoch should not update fork info
    network.onEpoch(UInt64.ONE);
    verifyNoMoreInteractions(discoveryNetwork);
  }

  @Test
  public void subscribeToSyncCommitteeSubnetId_shouldUpdateDiscoveryENR_oneUpdate() {
    final ArgumentCaptor<Iterable<Integer>> subnetsCaptor = subnetIdCaptor();

    assertThat(network.start()).isCompleted();
    network.subscribeToSyncCommitteeSubnetId(1);

    verify(discoveryNetwork).setSyncCommitteeSubnetSubscriptions(subnetsCaptor.capture());

    assertThat(subnetsCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(subnetsCaptor.getValue()).containsExactly(1);
  }

  @Test
  public void subscribeToSyncCommitteeSubnetId_shouldUpdateDiscoveryENR_multipleUpdates() {
    final ArgumentCaptor<Iterable<Integer>> subnetsCaptor = subnetIdCaptor();

    assertThat(network.start()).isCompleted();
    network.subscribeToSyncCommitteeSubnetId(1);
    network.subscribeToSyncCommitteeSubnetId(2);
    network.subscribeToSyncCommitteeSubnetId(3);

    verify(discoveryNetwork, times(3)).setSyncCommitteeSubnetSubscriptions(subnetsCaptor.capture());

    final List<Iterable<Integer>> capturedValues = subnetsCaptor.getAllValues();
    assertThat(capturedValues.size()).isEqualTo(3);
    assertThat(capturedValues.get(0)).containsExactly(1);
    assertThat(capturedValues.get(1)).containsExactlyInAnyOrder(1, 2);
    assertThat(capturedValues.get(2)).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  public void unsubscribeFromSyncCommitteeSubnetId_shouldUpdateDiscoveryENR() {
    final ArgumentCaptor<Iterable<Integer>> subnetsCaptor = subnetIdCaptor();

    assertThat(network.start()).isCompleted();
    network.subscribeToSyncCommitteeSubnetId(1);
    network.subscribeToSyncCommitteeSubnetId(2);
    network.subscribeToSyncCommitteeSubnetId(3);
    network.unsubscribeFromSyncCommitteeSubnetId(2);

    verify(discoveryNetwork, times(4)).setSyncCommitteeSubnetSubscriptions(subnetsCaptor.capture());

    final List<Iterable<Integer>> capturedValues = subnetsCaptor.getAllValues();
    assertThat(capturedValues.size()).isEqualTo(4);
    assertThat(capturedValues.get(0)).containsExactly(1);
    assertThat(capturedValues.get(1)).containsExactlyInAnyOrder(1, 2);
    assertThat(capturedValues.get(2)).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(capturedValues.get(3)).containsExactlyInAnyOrder(1, 3);
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<Iterable<Integer>> subnetIdCaptor() {
    return ArgumentCaptor.forClass(Iterable.class);
  }

  private void setupForkInfo() {
    // Set fork info
    phase0Fork = spec.getForkSchedule().getFork(UInt64.ZERO);
    altairFork = spec.getForkSchedule().getFork(altairForkEpoch);
    genesisValidatorsRoot = genesis.getState().getGenesis_validators_root();

    // Verify assumptions
    assertThat(phase0Fork.getCurrent_version()).isNotEqualTo(altairFork.getCurrent_version());
    assertThat(altairFork.getPrevious_version()).isEqualTo(phase0Fork.getCurrent_version());
    assertThat(altairFork.getEpoch()).isEqualTo(altairForkEpoch);
  }

  ActiveEth2P2PNetwork createNetwork() {
    return new ActiveEth2P2PNetwork(
        spec,
        asyncRunner,
        discoveryNetwork,
        peerManager,
        gossipForkManager,
        eventChannels,
        recentChainData,
        attestationSubnetService,
        syncCommitteeSubnetService,
        gossipEncoding,
        gossipConfigurator,
        processedAttestationSubscriptionProvider);
  }
}
