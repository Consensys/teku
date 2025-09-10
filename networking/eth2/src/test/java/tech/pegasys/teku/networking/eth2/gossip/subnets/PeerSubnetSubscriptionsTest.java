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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBitvectorImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.eth2.SubnetSubscriptionService;
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
  private static final NodeId PEER1 = new MockNodeId(1);
  private static final NodeId PEER2 = new MockNodeId(2);
  private static final NodeId PEER3 = new MockNodeId(3);
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
  private final Map<NodeId, SszBitvector> peer2subnets =
      ImmutableMap.<NodeId, SszBitvector>builder()
          .put(PEER1, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5, 6))
          .put(PEER2, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5))
          .put(PEER3, sszBitvectorSchema.ofBits(4, 5, 7))
          .build();

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

  @Test
  public void create_shouldSetUpExpectedSubscriptions() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    final Map<String, Collection<NodeId>> subscribersByTopic =
        ImmutableMap.<String, Collection<NodeId>>builder()
            .put("attnet_0", Set.of(PEER1, PEER2, PEER3))
            .put("attnet_1", Set.of(PEER1))
            .put("attnet_2", Set.of(PEER1, PEER3))
            .put("syncnet_1", Set.of(PEER2))
            .put("blocks", Set.of(PEER1, PEER2, PEER3))
            .build();
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(0)).isEqualTo(3);
    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(1)).isEqualTo(1);
    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(2)).isEqualTo(2);

    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(0)).isEqualTo(0);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(1)).isEqualTo(1);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(2)).isEqualTo(0);

    assertThat(subscriptions.getAttestationSubnetSubscriptions(PEER1))
        .isEqualTo(createAttnetsBitvector(0, 1, 2));
    assertThat(subscriptions.getAttestationSubnetSubscriptions(PEER2))
        .isEqualTo(createAttnetsBitvector(0));
    assertThat(subscriptions.getAttestationSubnetSubscriptions(PEER3))
        .isEqualTo(createAttnetsBitvector(0, 2));

    assertThat(subscriptions.getSyncCommitteeSubscriptions(PEER1))
        .isEqualTo(createSyncnetsBitvector());
    assertThat(subscriptions.getSyncCommitteeSubscriptions(PEER2))
        .isEqualTo(createSyncnetsBitvector(1));
    assertThat(subscriptions.getSyncCommitteeSubscriptions(PEER3))
        .isEqualTo(createSyncnetsBitvector());
  }

  @Test
  // We currently are subscribed to the 7 subnets we need through 3 peers.
  // We have to select the lowest scoring one to disconnect.
  // Should select PEER3 here because every subnet from PEER1 is covered by PEER2,
  // but PEER3 has a subnet not covered by PEER1
  public void shouldScoreExistingPeersAccordingToUniqueness() {
    dataColumnSubscriptions.setSubscriptions(IntList.of(0, 1, 2, 3, 4, 5, 6, 7));
    final Map<String, Collection<NodeId>> subscribersByTopic =
        makeSubscribersByTopic(
            Map.of(
                0, Set.of(PEER1, PEER2),
                1, Set.of(PEER1, PEER2),
                2, Set.of(PEER1, PEER2),
                3, Set.of(PEER1, PEER2),
                4, Set.of(PEER1, PEER2, PEER3),
                5, Set.of(PEER1, PEER2, PEER3),
                6, Set.of(PEER1),
                7, Set.of(PEER3)));
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenAnswer(
            invocation -> {
              final NodeId nodeId = new MockNodeId(invocation.getArgument(0));
              return Optional.ofNullable(peer2subnets.get(nodeId));
            });
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    verifySetup(subscriptions, subscribersByTopic);

    final PeerScorer scorer = subscriptions.createScorer();
    assertThat(
            Stream.of(PEER1, PEER2, PEER3)
                .sorted(Comparator.comparing((NodeId n) -> scorer.scoreExistingPeer(n)).reversed())
                .limit(2))
        .containsExactlyInAnyOrder(PEER1, PEER3);
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
                  .getDataColumnSidecarSubnetSubscriptions(nodeIdEntry.getKey())
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
    final Map<NodeId, SszBitvector> peer2subnets =
        ImmutableMap.<NodeId, SszBitvector>builder()
            .put(PEER1, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5, 6))
            .put(PEER2, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5))
            .put(PEER3, sszBitvectorSchema.ofBits(4, 5, 7))
            .build();
    final Map<String, Collection<NodeId>> subscribersByTopic = makeSubscribersByTopic(Map.of());
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenAnswer(
            invocation -> {
              final NodeId nodeId = new MockNodeId(invocation.getArgument(0));
              return Optional.ofNullable(peer2subnets.get(nodeId));
            });
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    verifySetup(subscriptions, subscribersByTopic);

    final PeerScorer scorer = subscriptions.createScorer();
    assertThat(
            Stream.<NodeId>of(PEER1, PEER2, PEER3)
                .sorted(
                    Comparator.<NodeId, Integer>comparing(
                            peerId ->
                                scorer.scoreCandidatePeer(
                                    makeCandidatePeer(peerId, peer2subnets.get(peerId).size())))
                        .reversed())
                .limit(2))
        .containsExactlyInAnyOrder(PEER1, PEER3);
  }

  @Test
  // We have an existing PEER1 which provides 6 of the 7 subnets we need. The candidates are
  // - PEER2 which provides 6 interesting of the 7 subnets, but not the one we currently lack.
  // - PEER3 which provides only 1 interesting of the 6 subnets, but the one we currently lack.
  // -> When setting MAX 1, we should select PEER3 as the new peer.
  public void shouldScoreCandidatePeersAccordingToUniqueness2() {
    dataColumnSubscriptions.setSubscriptions(IntList.of(0, 1, 2, 3, 4, 5, 6, 7));
    final Map<NodeId, SszBitvector> peer2subnets =
        ImmutableMap.<NodeId, SszBitvector>builder()
            .put(PEER1, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5, 6))
            .put(PEER2, sszBitvectorSchema.ofBits(0, 1, 2, 3, 4, 5))
            .put(PEER3, sszBitvectorSchema.ofBits(4, 5, 7))
            .build();
    final Map<String, Collection<NodeId>> subscribersByTopic =
        makeSubscribersByTopic(
            Map.of(
                0, Set.of(PEER1),
                1, Set.of(PEER1),
                2, Set.of(PEER1),
                3, Set.of(PEER1),
                4, Set.of(PEER1),
                5, Set.of(PEER1),
                6, Set.of(PEER1)));
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenAnswer(
            invocation -> {
              final NodeId nodeId = new MockNodeId(invocation.getArgument(0));
              return Optional.ofNullable(peer2subnets.get(nodeId));
            });
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    verifySetup(subscriptions, subscribersByTopic);

    final PeerScorer scorer = subscriptions.createScorer();
    assertThat(
            Stream.<NodeId>of(PEER2, PEER3)
                .sorted(
                    Comparator.<NodeId, Integer>comparing(
                            peerId ->
                                scorer.scoreCandidatePeer(
                                    makeCandidatePeer(peerId, peer2subnets.get(peerId).size())))
                        .reversed())
                .limit(1))
        .containsExactlyInAnyOrder(PEER3);
  }

  @Test
  // We have 100 PEERS numbered 0-99
  // 0-4: supernodes
  // 5-49: validator nodes serving 8 subnets, random but not subnets 126 and 127
  // 50-51: full nodes serving 4 subnets including 126, 127 and 2 random ones.
  // 52-99: full nodes serving 4 random subnets but all excluding 126, 127.
  // We want to be a supernode ourselves and serve all 128 from MAX 50 peers.
  // -> Should select peer 0,1,2,3,4,50,51 + 43 others.
  public void shouldScoreCandidatePeersInRealWorldScenario() {
    final List<Integer> allSubnets = IntStream.rangeClosed(0, 127).boxed().toList();
    dataColumnSubscriptions.setSubscriptions(allSubnets);
    final NodeId[] peers = new NodeId[100];
    final ImmutableMap.Builder<NodeId, SszBitvector> builder =
        ImmutableMap.<NodeId, SszBitvector>builder();
    final Random random = new Random();
    for (int i = 0; i < 100; i++) {
      peers[i] = new MockNodeId(i);
      if (i < 5) {
        builder.put(peers[i], sszBitvectorSchema.ofBits(allSubnets));
      } else if (i < 50) {
        builder.put(peers[i], sszBitvectorSchema.ofBits(random.ints(8, 0, 126).boxed().toList()));
      } else if (i < 52) {
        builder.put(
            peers[i],
            sszBitvectorSchema.ofBits(
                Stream.concat(random.ints(2, 0, 126).boxed(), Stream.of(126, 127)).toList()));
      } else {
        builder.put(peers[i], sszBitvectorSchema.ofBits(random.ints(4, 0, 126).boxed().toList()));
      }
    }
    final Map<NodeId, SszBitvector> peer2subnets = builder.build();
    assertThat(peer2subnets.size()).isEqualTo(100);
    final Map<String, Collection<NodeId>> subscribersByTopic =
        makeSubscribersByTopic(Map.of()); // no existing subsribers
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    when(nodeIdToDataColumnSidecarSubnetsCalculator.calculateSubnets(any(), any()))
        .thenAnswer(
            invocation -> {
              final NodeId nodeId = new MockNodeId(invocation.getArgument(0));
              return Optional.ofNullable(peer2subnets.get(nodeId));
            });
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    verifySetup(subscriptions, subscribersByTopic);

    final PeerScorer scorer = subscriptions.createScorer();
    assertThat(
            Stream.of(peers)
                .sorted(
                    Comparator.<NodeId, Integer>comparing(
                            peerId ->
                                scorer.scoreCandidatePeer(
                                    makeCandidatePeer(peerId, peer2subnets.get(peerId).size())))
                        .reversed())
                .limit(50))
        .containsAll(
            Stream.concat(Arrays.stream(peers).limit(5), Stream.of(peers[50], peers[51])).toList());
  }

  @NotNull
  private static Map<String, Collection<NodeId>> makeSubscribersByTopic(
      final Map<Integer, Set<NodeId>> existingSubscribers) {
    final ImmutableMap.Builder<String, Collection<NodeId>> builder =
        ImmutableMap.<String, Collection<NodeId>>builder();
    IntStream.range(0, SUBNET_COUNT)
        .forEach(
            i ->
                builder.put(
                    "data_column_sidecar_" + i,
                    existingSubscribers.containsKey(i)
                        ? existingSubscribers.get(i)
                        : Set.<NodeId>of()));

    return builder.build();
  }

  private DiscoveryPeer makeCandidatePeer(final NodeId peerId, final int dasCustodyCount) {
    return new DiscoveryPeer(
        dataStructureUtil.randomPublicKeyBytes(),
        peerId.toBytes(),
        new InetSocketAddress(8888),
        Optional.empty(),
        SszBitvectorImpl.ofBits(SszBitvectorSchema.create(128)),
        SszBitvectorImpl.ofBits(SszBitvectorSchema.create(128)),
        Optional.of(dasCustodyCount),
        Optional.empty());
  }

  @Test
  public void create_withNoSubscriptions() {
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(1)).isEqualTo(0);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(1)).isEqualTo(0);
    assertThat(subscriptions.getAttestationSubnetSubscriptions(PEER1))
        .isEqualTo(createAttnetsBitvector());
    assertThat(subscriptions.getSyncCommitteeSubscriptions(PEER1))
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
