/*
 * Copyright 2020 ConsenSys AG.
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

class PeerSubnetSubscriptionsTest {
  private static final NodeId PEER1 = new MockNodeId(1);
  private static final NodeId PEER2 = new MockNodeId(2);
  private static final NodeId PEER3 = new MockNodeId(3);
  private static final int TARGET_SUBSCRIBER_COUNT = 2;

  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final AttestationSubnetTopicProvider topicProvider =
      mock(AttestationSubnetTopicProvider.class);

  @BeforeEach
  void setUp() {
    when(topicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(invocation -> "subnet_" + invocation.getArgument(0));
  }

  @Test
  void shouldCreateFromTopicMap() {
    final Map<String, Collection<NodeId>> subscribersByTopic =
        ImmutableMap.<String, Collection<NodeId>>builder()
            .put("subnet_0", Set.of(PEER1, PEER2, PEER3))
            .put("subnet_1", Set.of(PEER1))
            .put("subnet_2", Set.of(PEER1, PEER3))
            .put("blocks", Set.of(PEER1, PEER2, PEER3))
            .build();
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    final PeerSubnetSubscriptions subscriptions =
        PeerSubnetSubscriptions.create(gossipNetwork, topicProvider, TARGET_SUBSCRIBER_COUNT);
    assertThat(subscriptions.getSubscriberCountForSubnet(0)).isEqualTo(3);
    assertThat(subscriptions.getSubscriberCountForSubnet(1)).isEqualTo(1);
    assertThat(subscriptions.getSubscriberCountForSubnet(2)).isEqualTo(2);

    assertThat(subscriptions.getSubscriptionsForPeer(PEER1)).isEqualTo(createBitvector(0, 1, 2));
    assertThat(subscriptions.getSubscriptionsForPeer(PEER2)).isEqualTo(createBitvector(0));
    assertThat(subscriptions.getSubscriptionsForPeer(PEER3)).isEqualTo(createBitvector(0, 2));
  }

  @Test
  void shouldReturnEmptyBitvectorWhenPeerHasNoSubscriptions() {
    final Bitvector subscriptions =
        PeerSubnetSubscriptions.create(gossipNetwork, topicProvider, TARGET_SUBSCRIBER_COUNT)
            .getSubscriptionsForPeer(PEER1);
    assertThat(subscriptions).isEqualTo(createBitvector());
  }

  @Test
  void shouldReturnZeroWhenSubnetHasNoSubscribers() {
    final int subscriberCount =
        PeerSubnetSubscriptions.create(gossipNetwork, topicProvider, TARGET_SUBSCRIBER_COUNT)
            .getSubscriberCountForSubnet(1);
    assertThat(subscriberCount).isEqualTo(0);
  }

  @Test
  void shouldRequireAdditionalPeersWhenNotAllSubnetsHaveEnoughSubscribers() {
    final int requiredPeers =
        PeerSubnetSubscriptions.create(gossipNetwork, topicProvider, TARGET_SUBSCRIBER_COUNT)
            .getSubscribersRequired();
    assertThat(requiredPeers).isEqualTo(TARGET_SUBSCRIBER_COUNT);
  }

  @Test
  void shouldRequireAdditionalPeersWhenAllSubnetsHaveSomeButNotEnoughSubscribers() {
    withSubscriberCountForAllSubnets(1);
    final int requiredPeers =
        PeerSubnetSubscriptions.create(gossipNetwork, topicProvider, TARGET_SUBSCRIBER_COUNT)
            .getSubscribersRequired();
    assertThat(requiredPeers).isEqualTo(TARGET_SUBSCRIBER_COUNT - 1);
  }

  @Test
  void shouldRequireAdditionalPeersWhenAllSubnetsHaveEnoughSubscribers() {
    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT);
    final int requiredPeers =
        PeerSubnetSubscriptions.create(gossipNetwork, topicProvider, TARGET_SUBSCRIBER_COUNT)
            .getSubscribersRequired();
    assertThat(requiredPeers).isZero();
  }

  private void withSubscriberCountForAllSubnets(int subscriberCount) {
    final List<NodeId> subscribers = new ArrayList<>();
    IntStream.range(0, subscriberCount).mapToObj(MockNodeId::new).forEach(subscribers::add);
    final Map<String, Collection<NodeId>> subscribersByTopic = new HashMap<>();
    IntStream.range(0, ATTESTATION_SUBNET_COUNT)
        .forEach(
            subnetId ->
                subscribersByTopic.put(topicProvider.getTopicForSubnet(subnetId), subscribers));
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
  }

  private Bitvector createBitvector(final Integer... subnets) {
    return new Bitvector(List.of(subnets), ATTESTATION_SUBNET_COUNT);
  }
}
