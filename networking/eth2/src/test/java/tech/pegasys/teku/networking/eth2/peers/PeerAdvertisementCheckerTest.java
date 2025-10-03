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

package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.eth2.ActiveEth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeIdToDataColumnSidecarSubnetsCalculator;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class PeerAdvertisementCheckerTest {
  private final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory = mock();
  private final ReputationManager reputationManager = mock(ReputationManager.class);

  private final NodeIdToDataColumnSidecarSubnetsCalculator
      nodeIdToDataColumnSidecarSubnetsCalculator =
          mock(NodeIdToDataColumnSidecarSubnetsCalculator.class);
  private final AtomicReference<ActiveEth2P2PNetwork> activeEth2P2PNetworkReference =
      new AtomicReference<>();
  private final P2PNetwork<?> network = mock(P2PNetwork.class);
  private final DiscoveryService discoveryService = mock(DiscoveryService.class);
  private final NodeId nodeId1 = new MockNodeId(1);
  private final UInt256 discoveryNodeId1 = UInt256.valueOf(1);

  private final SszBitvectorSchema<SszBitvector> subscriptionSchema =
      SszBitvectorSchema.create(128);

  private PeerAdvertisementChecker checker;
  private PeerSubnetSubscriptions subnetSubscriptions;
  private ActiveEth2P2PNetwork activeEth2P2PNetwork;

  @BeforeEach
  public void setup() {
    this.checker =
        new PeerAdvertisementChecker(
            peerSubnetSubscriptionsFactory,
            reputationManager,
            nodeIdToDataColumnSidecarSubnetsCalculator,
            activeEth2P2PNetworkReference);
    this.subnetSubscriptions = mock(PeerSubnetSubscriptions.class);
    this.activeEth2P2PNetwork = mock(ActiveEth2P2PNetwork.class);
    activeEth2P2PNetworkReference.set(activeEth2P2PNetwork);
  }

  @Test
  public void whenVerifyPeerAdvertisementWithNoActiveEth2P2PNetwork_IsGood() {
    final Peer peer = mock(Peer.class);
    activeEth2P2PNetworkReference.set(null);

    assertThat(checker.verifyPeerAdvertisement(peer, Map.of(), Map.of(), subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void whenVerifyPeerAdvertisementWithNoTopicsBySubscriber_IsGood() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);

    assertThat(checker.verifyPeerAdvertisement(peer, Map.of(), Map.of(), subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void whenVerifyPeerAdvertisementWithPeerNotFoundInActiveEth2P2PNetwork_IsGood() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);

    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(),
                subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void whenVerifyPeerAdvertisementWithNoDiscoveryPeerMap_IsGood() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));

    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(),
                subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void whenVerifyPeerAdvertisementNoAdvertisingNoSubscriptions_IsGood() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.getDiscoveryNodeId()).thenReturn(Optional.of(discoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));
    final DiscoveryPeer discoveryPeer = mock(DiscoveryPeer.class);

    setupSubscriptions(subnetSubscriptions, discoveryPeer, 0, 0, 0, 0, 0, 0);
    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(discoveryNodeId1, discoveryPeer),
                subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void whenVerifyPeerAdvertisementAdvertisingNoSyncCommitteeSubscriptions_IsBad() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.getDiscoveryNodeId()).thenReturn(Optional.of(discoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));
    final DiscoveryPeer discoveryPeer = mock(DiscoveryPeer.class);

    setupSubscriptions(subnetSubscriptions, discoveryPeer, 0, 0, 1, 0, 0, 0);
    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(discoveryNodeId1, discoveryPeer),
                subnetSubscriptions))
        .isFalse();
  }

  @Test
  public void whenVerifyPeerAdvertisementAdvertisingNoAttestationSubscriptions_IsBad() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.getDiscoveryNodeId()).thenReturn(Optional.of(discoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));
    final DiscoveryPeer discoveryPeer = mock(DiscoveryPeer.class);

    setupSubscriptions(subnetSubscriptions, discoveryPeer, 1, 0, 0, 0, 0, 0);
    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(discoveryNodeId1, discoveryPeer),
                subnetSubscriptions))
        .isFalse();
  }

  @Test
  public void whenVerifyPeerAdvertisementAdvertisingNoDataColumnSidecarSubnetSubscriptions_IsBad() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.getDiscoveryNodeId()).thenReturn(Optional.of(discoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));
    final DiscoveryPeer discoveryPeer = mock(DiscoveryPeer.class);

    setupSubscriptions(subnetSubscriptions, discoveryPeer, 0, 0, 0, 0, 4, 0);
    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(discoveryNodeId1, discoveryPeer),
                subnetSubscriptions))
        .isFalse();
  }

  @Test
  public void
      whenVerifyPeerAdvertisementAdvertisingLessThanActualDataColumnSidecarSubnetSubscriptions_IsGood() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.getDiscoveryNodeId()).thenReturn(Optional.of(discoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));
    final DiscoveryPeer discoveryPeer = mock(DiscoveryPeer.class);

    setupSubscriptions(subnetSubscriptions, discoveryPeer, 0, 0, 0, 0, 4, 8);
    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(discoveryNodeId1, discoveryPeer),
                subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void
      whenVerifyPeerAdvertisementAdvertisingLessThanActualAttestationSubnetSubscriptions_IsGood() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.getDiscoveryNodeId()).thenReturn(Optional.of(discoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));
    final DiscoveryPeer discoveryPeer = mock(DiscoveryPeer.class);

    setupSubscriptions(subnetSubscriptions, discoveryPeer, 3, 6, 0, 0, 0, 0);
    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(discoveryNodeId1, discoveryPeer),
                subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void
      whenVerifyPeerAdvertisementAdvertisingLessThanActualSyncCommitteeSubnetSubscriptions_IsGood() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.getDiscoveryNodeId()).thenReturn(Optional.of(discoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));
    final DiscoveryPeer discoveryPeer = mock(DiscoveryPeer.class);

    setupSubscriptions(subnetSubscriptions, discoveryPeer, 0, 0, 10, 20, 0, 0);
    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(discoveryNodeId1, discoveryPeer),
                subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void whenVerifyPeerAdvertisementAdvertisingLessThanActualSubscriptions_IsGood() {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId1);
    final Eth2Peer eth2Peer = mock(Eth2Peer.class);
    when(eth2Peer.getDiscoveryNodeId()).thenReturn(Optional.of(discoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(nodeId1)).thenReturn(Optional.of(eth2Peer));
    final DiscoveryPeer discoveryPeer = mock(DiscoveryPeer.class);

    setupSubscriptions(subnetSubscriptions, discoveryPeer, 3, 6, 10, 20, 4, 8);
    assertThat(
            checker.verifyPeerAdvertisement(
                peer,
                Map.of(nodeId1, List.of("data_column_sidecar_1")),
                Map.of(discoveryNodeId1, discoveryPeer),
                subnetSubscriptions))
        .isTrue();
  }

  @Test
  public void findFalseAdvertisingPeers_shouldReturnOnlyBadPeers() {
    final NodeId goodNodeId1 = new MockNodeId(1);
    final UInt256 goodDiscoveryNodeId1 = UInt256.valueOf(1);
    final NodeId goodNodeId2 = new MockNodeId(2);
    final UInt256 goodDiscoveryNodeId2 = UInt256.valueOf(2);
    final NodeId badNodeId3 = new MockNodeId(3);
    final UInt256 badDiscoveryNodeId3 = UInt256.valueOf(3);
    final NodeId goodNodeId4 = new MockNodeId(4);
    final UInt256 goodDiscoveryNodeId4 = UInt256.valueOf(4);
    final NodeId badNodeId5 = new MockNodeId(5);
    final UInt256 badDiscoveryNodeId5 = UInt256.valueOf(5);

    final DiscoveryPeer discoveryPeer1 = mock(DiscoveryPeer.class);
    when(discoveryPeer1.getNodeId()).thenReturn(goodDiscoveryNodeId1);
    final Peer peer1 = mock(Peer.class);
    when(peer1.getId()).thenReturn(goodNodeId1);
    final Eth2Peer eth2Peer1 = mock(Eth2Peer.class);
    when(eth2Peer1.getDiscoveryNodeId()).thenReturn(Optional.of(goodDiscoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(goodNodeId1)).thenReturn(Optional.of(eth2Peer1));
    setupSubscriptions(
        subnetSubscriptions, discoveryPeer1, goodNodeId1, goodDiscoveryNodeId1, 3, 6, 10, 20, 4, 8);

    final DiscoveryPeer discoveryPeer2 = mock(DiscoveryPeer.class);
    when(discoveryPeer2.getNodeId()).thenReturn(goodDiscoveryNodeId2);
    final Peer peer2 = mock(Peer.class);
    when(peer2.getId()).thenReturn(goodNodeId2);
    final Eth2Peer eth2Peer2 = mock(Eth2Peer.class);
    when(eth2Peer2.getDiscoveryNodeId()).thenReturn(Optional.of(goodDiscoveryNodeId2));
    when(activeEth2P2PNetwork.getPeer(goodNodeId2)).thenReturn(Optional.of(eth2Peer2));
    setupSubscriptions(
        subnetSubscriptions, discoveryPeer2, goodNodeId2, goodDiscoveryNodeId2, 3, 6, 10, 20, 4, 8);

    final DiscoveryPeer discoveryPeer3 = mock(DiscoveryPeer.class);
    when(discoveryPeer3.getNodeId()).thenReturn(badDiscoveryNodeId3);
    final Peer peer3 = mock(Peer.class);
    when(peer3.getId()).thenReturn(badNodeId3);
    final Eth2Peer eth2Peer3 = mock(Eth2Peer.class);
    when(eth2Peer3.getDiscoveryNodeId()).thenReturn(Optional.of(badDiscoveryNodeId3));
    when(activeEth2P2PNetwork.getPeer(badNodeId3)).thenReturn(Optional.of(eth2Peer3));
    setupSubscriptions(
        subnetSubscriptions, discoveryPeer3, badNodeId3, badDiscoveryNodeId3, 3, 1, 10, 20, 4, 8);

    final DiscoveryPeer discoveryPeer4 = mock(DiscoveryPeer.class);
    when(discoveryPeer4.getNodeId()).thenReturn(goodDiscoveryNodeId4);
    final Peer peer4 = mock(Peer.class);
    when(peer4.getId()).thenReturn(goodNodeId4);
    final Eth2Peer eth2Peer4 = mock(Eth2Peer.class);
    when(eth2Peer4.getDiscoveryNodeId()).thenReturn(Optional.of(goodDiscoveryNodeId4));
    when(activeEth2P2PNetwork.getPeer(goodNodeId4)).thenReturn(Optional.of(eth2Peer4));
    setupSubscriptions(
        subnetSubscriptions, discoveryPeer4, goodNodeId4, goodDiscoveryNodeId4, 3, 6, 10, 20, 4, 8);

    final DiscoveryPeer discoveryPeer5 = mock(DiscoveryPeer.class);
    when(discoveryPeer5.getNodeId()).thenReturn(badDiscoveryNodeId5);
    final Peer peer5 = mock(Peer.class);
    when(peer5.getId()).thenReturn(badNodeId5);
    final Eth2Peer eth2Peer5 = mock(Eth2Peer.class);
    when(eth2Peer5.getDiscoveryNodeId()).thenReturn(Optional.of(badDiscoveryNodeId5));
    when(activeEth2P2PNetwork.getPeer(badNodeId5)).thenReturn(Optional.of(eth2Peer5));
    setupSubscriptions(
        subnetSubscriptions, discoveryPeer5, badNodeId5, badDiscoveryNodeId5, 3, 6, 10, 20, 4, 0);

    when(network.getSubscribersByTopic())
        .thenReturn(
            Map.of(
                "attestation_1",
                List.of(goodNodeId1, goodNodeId2, goodNodeId4),
                "attestation_2",
                List.of(goodNodeId1, goodNodeId2, goodNodeId4),
                "attestation_3",
                List.of(goodNodeId1, goodNodeId2, goodNodeId4),
                // When peers have 0 subscriptions at all, they are skipped,
                // so adding some subscriptions for bad peers too
                "sync_committee_1",
                List.of(badNodeId3, badNodeId5)));

    when(discoveryService.streamKnownPeers())
        .thenReturn(
            Stream.of(
                discoveryPeer1, discoveryPeer2, discoveryPeer3, discoveryPeer4, discoveryPeer5));
    when(peerSubnetSubscriptionsFactory.create(any())).thenReturn(subnetSubscriptions);

    final List<Peer> falseAdvertisingPeers =
        checker.findFalseAdvertisingPeers(
            Stream.of(peer1, peer2, peer3, peer4, peer5), network, discoveryService);

    assertThat(falseAdvertisingPeers.size()).isEqualTo(2);
    assertThat(falseAdvertisingPeers.get(0)).isSameAs(peer3);
    assertThat(falseAdvertisingPeers.get(1)).isSameAs(peer5);
  }

  @Test
  public void findFalseAdvertisingPeers_doesNotFailOnEmptyStream() {
    final NodeId goodNodeId1 = new MockNodeId(1);
    final UInt256 goodDiscoveryNodeId1 = UInt256.valueOf(1);
    final DiscoveryPeer discoveryPeer1 = mock(DiscoveryPeer.class);
    when(discoveryPeer1.getNodeId()).thenReturn(goodDiscoveryNodeId1);
    final Eth2Peer eth2Peer1 = mock(Eth2Peer.class);
    when(eth2Peer1.getDiscoveryNodeId()).thenReturn(Optional.of(goodDiscoveryNodeId1));
    when(activeEth2P2PNetwork.getPeer(goodNodeId1)).thenReturn(Optional.of(eth2Peer1));
    setupSubscriptions(
        subnetSubscriptions, discoveryPeer1, goodNodeId1, goodDiscoveryNodeId1, 3, 6, 10, 20, 4, 8);

    when(network.getSubscribersByTopic())
        .thenReturn(
            Map.of(
                "attestation_1",
                List.of(goodNodeId1),
                "attestation_2",
                List.of(goodNodeId1),
                "attestation_3",
                List.of(goodNodeId1)));

    when(discoveryService.streamKnownPeers()).thenReturn(Stream.of(discoveryPeer1));
    when(peerSubnetSubscriptionsFactory.create(any())).thenReturn(subnetSubscriptions);

    final List<Peer> falseAdvertisingPeers =
        checker.findFalseAdvertisingPeers(Stream.of(), network, discoveryService);

    assertThat(falseAdvertisingPeers.isEmpty()).isTrue();
  }

  private void setupSubscriptions(
      final PeerSubnetSubscriptions subnetSubscriptionsMock,
      final DiscoveryPeer discoveryPeerMock,
      final int advertisedAttestationSubnets,
      final int subscribedAttestationSubnets,
      final int advertisedSyncCommitteeSubnets,
      final int subscribedSyncCommitteeSubnets,
      final int advertisedDataColumnSidecarSubnets,
      final int subscribedDataColumnSidecarSubnets) {
    setupSubscriptions(
        subnetSubscriptionsMock,
        discoveryPeerMock,
        nodeId1,
        discoveryNodeId1,
        advertisedAttestationSubnets,
        subscribedAttestationSubnets,
        advertisedSyncCommitteeSubnets,
        subscribedSyncCommitteeSubnets,
        advertisedDataColumnSidecarSubnets,
        subscribedDataColumnSidecarSubnets);
  }

  private void setupSubscriptions(
      final PeerSubnetSubscriptions subnetSubscriptionsMock,
      final DiscoveryPeer discoveryPeerMock,
      final NodeId nodeId,
      final UInt256 discoveryNodeId,
      final int advertisedAttestationSubnets,
      final int subscribedAttestationSubnets,
      final int advertisedSyncCommitteeSubnets,
      final int subscribedSyncCommitteeSubnets,
      final int advertisedDataColumnSidecarSubnets,
      final int subscribedDataColumnSidecarSubnets) {
    // actual
    when(subnetSubscriptionsMock.getAttestationSubnetSubscriptions(eq(nodeId)))
        .thenReturn(
            subscriptionSchema.createFromElements(
                IntStream.range(0, subscribedAttestationSubnets)
                    .mapToObj(__ -> true)
                    .map(SszBit::of)
                    .toList()));
    when(subnetSubscriptionsMock.getSyncCommitteeSubscriptions(eq(nodeId)))
        .thenReturn(
            subscriptionSchema.createFromElements(
                IntStream.range(0, subscribedSyncCommitteeSubnets)
                    .mapToObj(__ -> true)
                    .map(SszBit::of)
                    .toList()));
    when(subnetSubscriptionsMock.getDataColumnSidecarSubnetSubscriptions(eq(nodeId)))
        .thenReturn(
            subscriptionSchema.createFromElements(
                IntStream.range(0, subscribedDataColumnSidecarSubnets)
                    .mapToObj(__ -> true)
                    .map(SszBit::of)
                    .toList()));

    // advertised
    when(discoveryPeerMock.getPersistentAttestationSubnets())
        .thenReturn(
            subscriptionSchema.createFromElements(
                IntStream.range(0, advertisedAttestationSubnets)
                    .mapToObj(__ -> true)
                    .map(SszBit::of)
                    .toList()));
    when(discoveryPeerMock.getSyncCommitteeSubnets())
        .thenReturn(
            subscriptionSchema.createFromElements(
                IntStream.range(0, advertisedSyncCommitteeSubnets)
                    .mapToObj(__ -> true)
                    .map(SszBit::of)
                    .toList()));
    when(discoveryPeerMock.getDasCustodyGroupCount())
        .thenReturn(Optional.of(advertisedDataColumnSidecarSubnets));
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(
            eq(discoveryNodeId), eq(Optional.of(advertisedDataColumnSidecarSubnets))))
        .thenReturn(
            Optional.of(
                subscriptionSchema.createFromElements(
                    IntStream.range(0, advertisedDataColumnSidecarSubnets)
                        .mapToObj(__ -> true)
                        .map(SszBit::of)
                        .toList())));
  }
}
