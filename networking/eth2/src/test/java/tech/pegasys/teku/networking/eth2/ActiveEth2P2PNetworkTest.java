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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
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
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
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
        new ForkInfo(phase0Fork, genesis.getState().getGenesisValidatorsRoot());
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
    asyncRunner.executeDueActions();
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

  @Test
  void onSyncStateChanged_shouldEnableGossipWhenInSync() {
    // Current slot is a long way beyond the chain head
    storageSystem.chainUpdater().setCurrentSlot(UInt64.valueOf(1000));

    assertThat(network.start()).isCompleted();
    // Won't start gossip as chain head is too old
    verify(gossipForkManager, never()).configureGossipForEpoch(any());

    network.onSyncStateChanged(true, false);

    // Even though we're a long way behind, start gossip because we believe we're in sync
    verify(gossipForkManager).configureGossipForEpoch(any());
  }

  @Test
  void onSyncStateChanged_shouldStopGossipWhenTooFarBehindAndNotInSync() {
    // Current slot is a long way beyond the chain head
    storageSystem.chainUpdater().setCurrentSlot(UInt64.valueOf(1000));

    assertThat(network.start()).isCompleted();
    network.onSyncStateChanged(true, false);
    // Even though we're a long way behind, start gossip because we believe we're in sync
    verify(gossipForkManager).configureGossipForEpoch(any());

    network.onSyncStateChanged(false, false);
    verify(gossipForkManager).stopGossip();
  }

  @Test
  void onSyncStateChanged_shouldNotifyForkManagerOfOptimisticSyncState() {
    assertThat(network.start()).isCompleted();

    network.onSyncStateChanged(false, true);
    verify(gossipForkManager).onOptimisticHeadChanged(true);

    network.onSyncStateChanged(false, false);
    verify(gossipForkManager).onOptimisticHeadChanged(false);

    network.onSyncStateChanged(true, true);
    verify(gossipForkManager, times(2)).onOptimisticHeadChanged(true);

    network.onSyncStateChanged(true, false);
    verify(gossipForkManager, times(2)).onOptimisticHeadChanged(false);
  }

  @Test
  void onSyncStateChanged_shouldNotResultInMultipleSubscriptions() {
    // Current slot is a long way beyond the chain head
    storageSystem.chainUpdater().setCurrentSlot(UInt64.valueOf(1000));

    assertThat(network.start()).isCompleted();
    // Won't start gossip as chain head is too old
    verify(gossipForkManager, never()).configureGossipForEpoch(any());

    network.onSyncStateChanged(true, false);
    verify(gossipForkManager).configureGossipForEpoch(any());
    assertThat(subscribers.getSubscriberCount()).isEqualTo(1);
    verify(eventChannels, times(1)).subscribe(eq(BlockGossipChannel.class), any());

    network.onSyncStateChanged(false, false);
    verify(gossipForkManager).stopGossip();

    network.onSyncStateChanged(true, false);
    verify(gossipForkManager, times(2)).configureGossipForEpoch(any());
    // Can't unsubscribe from these so should only subscribe once
    assertThat(subscribers.getSubscriberCount()).isEqualTo(1);
    verify(eventChannels, times(1)).subscribe(eq(BlockGossipChannel.class), any());
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<Iterable<Integer>> subnetIdCaptor() {
    return ArgumentCaptor.forClass(Iterable.class);
  }

  private void setupForkInfo() {
    // Set fork info
    phase0Fork = spec.getForkSchedule().getFork(UInt64.ZERO);
    altairFork = spec.getForkSchedule().getFork(altairForkEpoch);
    genesisValidatorsRoot = genesis.getState().getGenesisValidatorsRoot();

    // Verify assumptions
    assertThat(phase0Fork.getCurrentVersion()).isNotEqualTo(altairFork.getCurrentVersion());
    assertThat(altairFork.getPreviousVersion()).isEqualTo(phase0Fork.getCurrentVersion());
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
