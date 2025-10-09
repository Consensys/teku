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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBitvectorImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.eth2.SubnetSubscriptionService;
import tech.pegasys.teku.networking.eth2.peers.PeerId;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PeerSubnetSubscriptionsTest {
  private static final PeerId EXISTING_PEER1 = PeerId.fromExistingId(new MockNodeId(1));
  private static final PeerId EXISTING_PEER2 = PeerId.fromExistingId(new MockNodeId(2));
  private static final PeerId EXISTING_PEER3 = PeerId.fromExistingId(new MockNodeId(3));
  private static final PeerId CANDIDATE_PEER1 = PeerId.fromCandidateId(new MockNodeId(1).toBytes());
  private static final PeerId CANDIDATE_PEER2 = PeerId.fromCandidateId(new MockNodeId(2).toBytes());
  private static final PeerId CANDIDATE_PEER3 = PeerId.fromCandidateId(new MockNodeId(3).toBytes());
  private static final int TARGET_SUBSCRIBER_COUNT = 2;
  public static final int SUBNET_COUNT = 128;

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final SettableLabelledGauge subnetPeerCountGauge = mock(SettableLabelledGauge.class);
  final Supplier<SpecVersion> currentSpecVersionSupplier = spec::getGenesisSpec;
  private final SchemaDefinitionsSupplier currentSchemaDefinitions =
      spec::getGenesisSchemaDefinitions;
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final AttestationSubnetTopicProvider attestationTopicProvider =
      mock(AttestationSubnetTopicProvider.class);
  private final SyncCommitteeSubnetTopicProvider syncCommitteeTopicProvider =
      mock(SyncCommitteeSubnetTopicProvider.class);
  private final DataColumnSidecarSubnetTopicProvider dataColumnSidecarSubnetTopicProvider =
      mock(DataColumnSidecarSubnetTopicProvider.class);
  private final SubnetSubscriptionService syncnetSubscriptions = new SubnetSubscriptionService();
  private final SubnetSubscriptionService dataColumnSubscriptions = new SubnetSubscriptionService();
  private final NodeIdToDataColumnSidecarSubnetsCalculator
      nodeIdToDataColumnSidecarSubnetsCalculator =
          mock(NodeIdToDataColumnSidecarSubnetsCalculator.class);
  private final SszBitvectorSchema<?> sszBitvectorSchema = SszBitvectorSchema.create(SUBNET_COUNT);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @BeforeEach
  public void setUp() {
    when(attestationTopicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(invocation -> "attnet_" + invocation.getArgument(0));
    when(syncCommitteeTopicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(invocation -> "syncnet_" + invocation.getArgument(0));
    when(dataColumnSidecarSubnetTopicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(invocation -> "data_column_sidecar_" + invocation.getArgument(0));
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenReturn(Optional.empty());
  }

  private Set<NodeId> extractNodeIds(final PeerId... peers) {
    return Arrays.stream(peers)
        .filter(PeerId::isExisting)
        .map(PeerId::getExistingId)
        .flatMap(Optional::stream)
        .collect(Collectors.toSet());
  }

  @Test
  public void create_shouldSetUpExpectedSubscriptions() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    final Map<String, Collection<NodeId>> subscribersByTopic =
        ImmutableMap.<String, Collection<NodeId>>builder()
            .put("attnet_0", extractNodeIds(EXISTING_PEER1, EXISTING_PEER2, EXISTING_PEER3))
            .put("attnet_1", extractNodeIds(EXISTING_PEER1))
            .put("attnet_2", extractNodeIds(EXISTING_PEER1, EXISTING_PEER3))
            .put("syncnet_1", extractNodeIds(EXISTING_PEER2))
            .put("blocks", extractNodeIds(EXISTING_PEER1, EXISTING_PEER2, EXISTING_PEER3))
            .build();
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(0)).isEqualTo(3);
    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(1)).isEqualTo(1);
    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(2)).isEqualTo(2);

    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(0)).isEqualTo(0);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(1)).isEqualTo(1);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(2)).isEqualTo(0);

    assertThat(subscriptions.getAttestationSubnetSubscriptions(EXISTING_PEER1))
        .isEqualTo(createAttnetsBitvector(0, 1, 2));
    assertThat(subscriptions.getAttestationSubnetSubscriptions(EXISTING_PEER2))
        .isEqualTo(createAttnetsBitvector(0));
    assertThat(subscriptions.getAttestationSubnetSubscriptions(EXISTING_PEER3))
        .isEqualTo(createAttnetsBitvector(0, 2));

    assertThat(subscriptions.getSyncCommitteeSubscriptions(EXISTING_PEER1))
        .isEqualTo(createSyncnetsBitvector());
    assertThat(subscriptions.getSyncCommitteeSubscriptions(EXISTING_PEER2))
        .isEqualTo(createSyncnetsBitvector(1));
    assertThat(subscriptions.getSyncCommitteeSubscriptions(EXISTING_PEER3))
        .isEqualTo(createSyncnetsBitvector());
  }

  @Test
  // We currently are subscribed to the 7 subnets we need through 3 peers.
  // We have to select the lowest scoring one to disconnect.
  // Should select PEER2 here because every subnet from PEER2 is covered by PEER1,
  // but PEER3 has a subnet not covered by PEER1
  public void shouldScoreExistingPeersAccordingToUniqueness() {
    dataColumnSubscriptions.setSubscriptions(IntList.of(0, 1, 2, 3, 4, 5, 6, 7));
    final Map<PeerId, SszBitvector> peer2subnets =
        ImmutableMap.<PeerId, SszBitvector>builder()
            .put(EXISTING_PEER1, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5, 6))
            .put(EXISTING_PEER2, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5))
            .put(EXISTING_PEER3, sszBitvectorSchema.ofBits(4, 5, 7))
            .build();
    final Map<String, Collection<NodeId>> subscribersByTopic =
        makeSubscribersByTopic(
            Map.of(
                0, extractNodeIds(EXISTING_PEER1, EXISTING_PEER2),
                1, extractNodeIds(EXISTING_PEER1, EXISTING_PEER2),
                2, extractNodeIds(EXISTING_PEER1, EXISTING_PEER2),
                3, extractNodeIds(EXISTING_PEER1, EXISTING_PEER2),
                4, extractNodeIds(EXISTING_PEER1, EXISTING_PEER2, EXISTING_PEER3),
                5, extractNodeIds(EXISTING_PEER1, EXISTING_PEER2, EXISTING_PEER3),
                6, extractNodeIds(EXISTING_PEER1, EXISTING_PEER3)));
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenAnswer(
            invocation -> {
              final PeerId peerId = invocation.getArgument(0);
              return Optional.ofNullable(peer2subnets.get(peerId));
            });
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    verifySetup(subscriptions, subscribersByTopic);

    final PeerScorer scorer = subscriptions.createScorer();
    assertThat(
            scorer
                .selectCandidatePeers(
                    makeCandidatePeers(
                        List.of(CANDIDATE_PEER1, CANDIDATE_PEER2, CANDIDATE_PEER3), peer2subnets),
                    2)
                .stream()
                .map(candidate -> PeerId.fromCandidateId(candidate.getNodeId()))
                .toList())
        .containsExactlyInAnyOrder(CANDIDATE_PEER1, CANDIDATE_PEER3);
  }

  private void verifySetup(
      final PeerSubnetSubscriptions subscriptions,
      final Map<String, Collection<NodeId>> subscribersByTopic) {

    final Set<Integer> expectedRelevantSubnets = dataColumnSubscriptions.getSubnets();
    final Map<NodeId, int[]> expectedProvidedSubnetCountPerPeer = new HashMap<>();
    for (int i = 0; i < SUBNET_COUNT; i++) {
      Collection<NodeId> nodeIds =
          subscribersByTopic.getOrDefault("data_column_sidecar_" + i, Collections.emptyList());
      final int expectedSubscriberCount = nodeIds.size();
      assertThat(subscriptions.getSubscriberCountForDataColumnSidecarSubnet(i))
          .isEqualTo(expectedSubscriberCount);
      if (expectedRelevantSubnets.contains(i)) {
        assertThat(subscriptions.isDataColumnSidecarSubnetRelevant(i)).isTrue();
      } else {
        assertThat(subscriptions.isDataColumnSidecarSubnetRelevant(i)).isFalse();
      }
      for (NodeId nodeId : nodeIds) {
        int[] countPerPeer =
            expectedProvidedSubnetCountPerPeer.getOrDefault(nodeId, new int[SUBNET_COUNT]);
        countPerPeer[i]++;
        expectedProvidedSubnetCountPerPeer.put(nodeId, countPerPeer);
      }
    }
    for (Map.Entry<NodeId, int[]> nodeIdEntry : expectedProvidedSubnetCountPerPeer.entrySet()) {
      assertThat(
              subscriptions
                  .getDataColumnSidecarSubnetSubscriptions(
                      PeerId.fromCandidateId(nodeIdEntry.getKey().toBytes()))
                  .getAllSetBits()
                  .intStream()
                  .boxed()
                  .toList())
          .containsExactlyInAnyOrder(
              IntStream.range(0, SUBNET_COUNT)
                  .filter(i -> nodeIdEntry.getValue()[i] > 0)
                  .boxed()
                  .toArray(Integer[]::new));
    }
  }

  @Test
  // There are no existing peers and we require 7 subnets. The candidates are
  // - PEER1 which provides 6 of the 7 subnets.
  // - PEER2 which provides 5 of the 7 subnets.
  // - PEER3 which provides 3 of the 7 subnets
  // -> When setting Max 2, we should select PEER1 and PEER3 as the new peers.
  public void shouldScoreCandidatePeersAccordingToUniqueness1() {
    dataColumnSubscriptions.setSubscriptions(IntList.of(0, 1, 2, 3, 4, 5, 6, 7));
    final Map<PeerId, SszBitvector> peer2subnets =
        ImmutableMap.<PeerId, SszBitvector>builder()
            .put(CANDIDATE_PEER1, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5, 6))
            .put(CANDIDATE_PEER2, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5))
            .put(CANDIDATE_PEER3, sszBitvectorSchema.ofBits(4, 5, 7))
            .build();
    final Map<String, Collection<NodeId>> subscribersByTopic = makeSubscribersByTopic(Map.of());
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenAnswer(
            invocation -> {
              final PeerId peerId = invocation.getArgument(0);
              return Optional.ofNullable(peer2subnets.get(peerId));
            });
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    verifySetup(subscriptions, subscribersByTopic);

    final PeerScorer scorer = subscriptions.createScorer();
    assertThat(
            scorer
                .selectCandidatePeers(
                    makeCandidatePeers(
                        List.of(CANDIDATE_PEER1, CANDIDATE_PEER2, CANDIDATE_PEER3), peer2subnets),
                    2)
                .stream()
                .map(candidate -> PeerId.fromCandidateId(candidate.getNodeId()))
                .toList())
        .containsExactlyInAnyOrder(CANDIDATE_PEER1, CANDIDATE_PEER3);
  }

  @Test
  // We have an existing PEER1 which provides 7 of the 8 subnets we need. The candidates are
  // - PEER2 which provides 6 interesting of the 8 subnets, but not the one we currently lack.
  // - PEER3 which provides 3 of the 8 subnets, including the one we currently lack.
  // -> When setting MAX 1, we should select PEER3 as the new peer.
  public void shouldScoreCandidatePeersAccordingToUniqueness2() {
    dataColumnSubscriptions.setSubscriptions(IntList.of(0, 1, 2, 3, 4, 5, 6, 7));
    final Map<PeerId, SszBitvector> peer2subnets =
        ImmutableMap.<PeerId, SszBitvector>builder()
            .put(EXISTING_PEER1, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5, 6))
            .put(CANDIDATE_PEER2, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5))
            .put(CANDIDATE_PEER3, sszBitvectorSchema.ofBits(4, 5, 7))
            .build();
    final Map<String, Collection<NodeId>> subscribersByTopic =
        makeSubscribersByTopic(
            Map.of(
                0, extractNodeIds(EXISTING_PEER1),
                1, extractNodeIds(EXISTING_PEER1),
                2, extractNodeIds(EXISTING_PEER1),
                3, extractNodeIds(EXISTING_PEER1),
                4, extractNodeIds(EXISTING_PEER1),
                5, extractNodeIds(EXISTING_PEER1),
                6, extractNodeIds(EXISTING_PEER1)));
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenAnswer(
            invocation -> {
              final PeerId peerId = invocation.getArgument(0);
              return Optional.ofNullable(peer2subnets.get(peerId));
            });
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    verifySetup(subscriptions, subscribersByTopic);

    final PeerScorer scorer = subscriptions.createScorer();
    assertThat(
            scorer
                .selectCandidatePeers(
                    makeCandidatePeers(List.of(CANDIDATE_PEER2, CANDIDATE_PEER3), peer2subnets), 1)
                .stream()
                .map(candidate -> PeerId.fromCandidateId(candidate.getNodeId()))
                .toList())
        .containsExactlyInAnyOrder(CANDIDATE_PEER3);
  }

  @Test
  // We have 100 PEERS numbered 0-99
  // 0-48: validator nodes serving 8 subnets, random but not subnets 126 and 127
  // 50-97: full nodes serving 4 subnets, but not subnets 126 and 127
  // 98: supernode
  // 99: node service subnets 0,1, 126 and 127.
  // We want to be a supernode ourselves and serve all 128 from MAX 50 peers.
  // -> Should select peer 98,99 + 48 others so that all subnets are covered.
  public void shouldScoreCandidatePeersInRealWorldScenario() {
    final List<Integer> allSubnets = IntStream.rangeClosed(0, 127).boxed().toList();
    dataColumnSubscriptions.setSubscriptions(allSubnets);
    final PeerId[] peers = new PeerId[100];
    final ImmutableMap.Builder<PeerId, SszBitvector> builder = ImmutableMap.builder();
    for (int i = 0; i < 100; i++) {
      peers[i] = PeerId.fromCandidateId(new MockNodeId(i).toBytes());
      if (i < 49) {
        int subnetStart = i / 3 * 8 % 126;
        builder.put(
            peers[i],
            sszBitvectorSchema.ofBits(
                IntStream.rangeClosed(subnetStart, Math.min(subnetStart + 7, 125))
                    .boxed()
                    .toList()));
      } else if (i < 98) {
        int subnetStart = (i - 50) / 3 * 4 % 122;
        builder.put(
            peers[i],
            sszBitvectorSchema.ofBits(
                IntStream.rangeClosed(subnetStart, Math.min(subnetStart + 3, 125))
                    .boxed()
                    .toList()));

      } else if (i == 98) {
        builder.put(peers[i], sszBitvectorSchema.ofBits(allSubnets));
      } else {
        builder.put(peers[i], sszBitvectorSchema.ofBits(0, 1, 126, 127));
      }
    }
    final Map<PeerId, SszBitvector> peer2subnets = builder.build();
    assertThat(peer2subnets.size()).isEqualTo(100);
    final Map<String, Collection<NodeId>> subscribersByTopic =
        makeSubscribersByTopic(Map.of()); // no existing subscribers
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenAnswer(
            invocation -> {
              final PeerId peerId = invocation.getArgument(0);
              return Optional.ofNullable(peer2subnets.get(peerId));
            });
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    verifySetup(subscriptions, subscribersByTopic);

    final PeerScorer scorer = subscriptions.createScorer();
    assertThat(
            scorer
                .selectCandidatePeers(
                    makeCandidatePeers(Arrays.stream(peers).toList(), peer2subnets), 50)
                .stream()
                .map(candidate -> PeerId.fromCandidateId(candidate.getNodeId()))
                .toList())
        .containsAll(List.of(peers[98], peers[99]));
  }

  @NotNull
  private static Map<String, Collection<NodeId>> makeSubscribersByTopic(
      final Map<Integer, Set<NodeId>> existingSubscribers) {
    final ImmutableMap.Builder<String, Collection<NodeId>> builder = ImmutableMap.builder();
    IntStream.range(0, SUBNET_COUNT)
        .forEach(
            i ->
                builder.put(
                    "data_column_sidecar_" + i,
                    existingSubscribers.containsKey(i) ? existingSubscribers.get(i) : Set.of()));

    return builder.build();
  }

  private List<DiscoveryPeer> makeCandidatePeers(
      final List<PeerId> peerIds, final Map<PeerId, SszBitvector> peer2subnets) {
    return peerIds.stream()
        .filter(PeerId::isCandidate)
        .map(
            peerId ->
                new DiscoveryPeer(
                    dataStructureUtil.randomPublicKeyBytes(),
                    peerId.getCandidateId(),
                    new InetSocketAddress(8888),
                    Optional.empty(),
                    SszBitvectorImpl.ofBits(SszBitvectorSchema.create(128)),
                    SszBitvectorImpl.ofBits(SszBitvectorSchema.create(128)),
                    Optional.of(peer2subnets.get(peerId).size()),
                    Optional.empty()))
        .toList();
  }

  @Test
  public void create_withNoSubscriptions() {
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(1)).isEqualTo(0);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(1)).isEqualTo(0);
    assertThat(subscriptions.getAttestationSubnetSubscriptions(EXISTING_PEER1))
        .isEqualTo(createAttnetsBitvector());
    assertThat(subscriptions.getSyncCommitteeSubscriptions(EXISTING_PEER1))
        .isEqualTo(createSyncnetsBitvector());
  }

  @Test
  public void getSubscribersRequired_withNoSubscriptions() {
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscribersRequired()).isEqualTo(TARGET_SUBSCRIBER_COUNT);
  }

  @Test
  public void getSubscribersRequired_allSubnetsAreJustBelowTarget() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    withSubscriberCountForAllSubnets(1);
    final int requiredPeers = createPeerSubnetSubscriptions().getSubscribersRequired();
    assertThat(requiredPeers).isEqualTo(TARGET_SUBSCRIBER_COUNT - 1);
  }

  @Test
  public void getSubscribersRequired_allSubnetsHaveExactlyEnoughSubscribers() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT);
    final int requiredPeers = createPeerSubnetSubscriptions().getSubscribersRequired();
    assertThat(requiredPeers).isZero();
  }

  @Test
  public void getSubscribersRequired_allSubnetsOverlyFull() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT + 1);
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscribersRequired()).isEqualTo(0);
  }

  @Test
  public void getSubscribersRequired_onlySyncnetsAreBelowTarget() {
    // Set up attnets to be full, syncnets to be empty
    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT);
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    final int requiredPeers = createPeerSubnetSubscriptions().getSubscribersRequired();
    assertThat(requiredPeers).isEqualTo(TARGET_SUBSCRIBER_COUNT);
  }

  @Test
  public void getSubscribersRequired_attnetsAreFull_noRequiredSyncnets() {
    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT + 1);

    final int requiredPeers = createPeerSubnetSubscriptions().getSubscribersRequired();
    assertThat(requiredPeers).isEqualTo(0);
  }

  @Test
  public void isSyncCommitteeSubnetRelevant() {
    syncnetSubscriptions.setSubscriptions(IntList.of(1, 3));
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.isSyncCommitteeSubnetRelevant(0)).isFalse();
    assertThat(subscriptions.isSyncCommitteeSubnetRelevant(1)).isTrue();
    assertThat(subscriptions.isSyncCommitteeSubnetRelevant(2)).isFalse();
    assertThat(subscriptions.isSyncCommitteeSubnetRelevant(3)).isTrue();
  }

  @Test
  public void isAttestationSubnetRelevant() {
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    final int attSubnetsCount = currentSchemaDefinitions.getAttnetsENRFieldSchema().getLength();
    for (int i = 0; i < attSubnetsCount; i++) {
      assertThat(subscriptions.isAttestationSubnetRelevant(i)).isTrue();
    }

    assertThat(subscriptions.isAttestationSubnetRelevant(attSubnetsCount)).isFalse();
    assertThat(subscriptions.isAttestationSubnetRelevant(attSubnetsCount + 1)).isFalse();
  }

  private PeerSubnetSubscriptions createPeerSubnetSubscriptions() {
    return PeerSubnetSubscriptions.create(
        currentSpecVersionSupplier.get(),
        nodeIdToDataColumnSidecarSubnetsCalculator,
        gossipNetwork,
        attestationTopicProvider,
        syncCommitteeTopicProvider,
        syncnetSubscriptions,
        dataColumnSidecarSubnetTopicProvider,
        dataColumnSubscriptions,
        TARGET_SUBSCRIBER_COUNT,
        subnetPeerCountGauge);
  }

  private void withSubscriberCountForAllSubnets(final int subscriberCount) {
    // Set up subscribers
    final List<NodeId> subscribers = new ArrayList<>();
    IntStream.range(0, subscriberCount).mapToObj(MockNodeId::new).forEach(subscribers::add);
    // Set up attestation topic subscriptions
    final Map<String, Collection<NodeId>> subscribersByTopic = new HashMap<>();
    IntStream.range(0, spec.getNetworkingConfig().getAttestationSubnetCount())
        .forEach(
            subnetId ->
                subscribersByTopic.put(
                    attestationTopicProvider.getTopicForSubnet(subnetId), subscribers));
    // Set up sync committee topic subscriptions
    syncnetSubscriptions
        .getSubnets()
        .forEach(
            subnetId ->
                subscribersByTopic.put(
                    syncCommitteeTopicProvider.getTopicForSubnet(subnetId), subscribers));

    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
  }

  private SszBitvector createAttnetsBitvector(final int... subnets) {
    return currentSchemaDefinitions.getAttnetsENRFieldSchema().ofBits(subnets);
  }

  private SszBitvector createSyncnetsBitvector(final int... subnets) {
    return currentSchemaDefinitions.getSyncnetsENRFieldSchema().ofBits(subnets);
  }
}
