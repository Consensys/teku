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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.eth2.SubnetSubscriptionService;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;

class PeerSubnetSubscriptionsTest {
  private static final NodeId PEER1 = new MockNodeId(1);
  private static final NodeId PEER2 = new MockNodeId(2);
  private static final NodeId PEER3 = new MockNodeId(3);
  private static final int TARGET_SUBSCRIBER_COUNT = 2;

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final SettableLabelledGauge subnetPeerCountGauge = mock(SettableLabelledGauge.class);
  private final SchemaDefinitionsSupplier currentSchemaDefinitions =
      spec::getGenesisSchemaDefinitions;
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final AttestationSubnetTopicProvider attestationTopicProvider =
      mock(AttestationSubnetTopicProvider.class);
  private final SyncCommitteeSubnetTopicProvider syncCommitteeTopicProvider =
      mock(SyncCommitteeSubnetTopicProvider.class);
  private final SubnetSubscriptionService syncnetSubscriptions = new SubnetSubscriptionService();

  @BeforeEach
  public void setUp() {
    when(attestationTopicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(invocation -> "attnet_" + invocation.getArgument(0));
    when(syncCommitteeTopicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(invocation -> "syncnet_" + invocation.getArgument(0));
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
        currentSchemaDefinitions,
        gossipNetwork,
        attestationTopicProvider,
        syncCommitteeTopicProvider,
        syncnetSubscriptions,
        TARGET_SUBSCRIBER_COUNT,
        subnetPeerCountGauge);
  }

  private void withSubscriberCountForAllSubnets(int subscriberCount) {
    // Set up subscribers
    final List<NodeId> subscribers = new ArrayList<>();
    IntStream.range(0, subscriberCount).mapToObj(MockNodeId::new).forEach(subscribers::add);
    // Set up attestation topic subscriptions
    final Map<String, Collection<NodeId>> subscribersByTopic = new HashMap<>();
    IntStream.range(0, ATTESTATION_SUBNET_COUNT)
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
