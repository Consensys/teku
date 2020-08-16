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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipedOperationConsumer;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationSubnetSubscriptionsTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData =
      MemoryOnlyRecentChainData.create(mock(EventBus.class));
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  @SuppressWarnings("unchecked")
  private final GossipedOperationConsumer<ValidateableAttestation> attestationConsumer =
      mock(GossipedOperationConsumer.class);

  private AttestationSubnetSubscriptions subnetSubscriptions;

  @BeforeEach
  void setUp() {
    BeaconChainUtil.create(0, recentChainData).initializeStorage();
    subnetSubscriptions =
        new AttestationSubnetSubscriptions(
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            mock(AttestationValidator.class),
            recentChainData,
            attestationConsumer);

    when(gossipNetwork.subscribe(any(), any())).thenReturn(mock(TopicChannel.class));
  }

  @Test
  void getChannelReturnsEmptyIfNotSubscribedToSubnet() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final Attestation attestation2 = dataStructureUtil.randomAttestation();
    int subnetId = computeSubnetId(attestation);
    assertThat(computeSubnetId(attestation2)).isNotEqualTo(subnetId); // Sanity check
    subnetSubscriptions.subscribeToSubnetId(subnetId);
    assertThatSafeFuture(subnetSubscriptions.getChannel(attestation))
        .isCompletedWithNonEmptyOptional();
    assertThatSafeFuture(subnetSubscriptions.getChannel(attestation2))
        .isCompletedWithEmptyOptional();
  }

  @Test
  void getChannelReturnsTheChannelFromSubnet() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    int subnetId = computeSubnetId(attestation);
    subnetSubscriptions.subscribeToSubnetId(subnetId);
    assertThatSafeFuture(subnetSubscriptions.getChannel(attestation))
        .isCompletedWithNonEmptyOptional();
  }

  @Test
  void shouldSubscribeToCommitteesOnDifferentSubnets() {
    final Attestation attestation1 = dataStructureUtil.randomAttestation();
    final Attestation attestation2 = dataStructureUtil.randomAttestation();
    int subnetId1 = computeSubnetId(attestation1);
    int subnetId2 = computeSubnetId(attestation2);
    assertThat(subnetId1).isNotEqualTo(subnetId2); // Sanity check

    TopicChannel topicChannel1 = mock(TopicChannel.class);
    TopicChannel topicChannel2 = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("beacon_attestation_" + subnetId1), any()))
        .thenReturn(topicChannel1);
    when(gossipNetwork.subscribe(contains("beacon_attestation_" + subnetId2), any()))
        .thenReturn(topicChannel2);

    subnetSubscriptions.subscribeToSubnetId(subnetId1);
    subnetSubscriptions.subscribeToSubnetId(subnetId2);

    verifyNoInteractions(topicChannel2);

    verify(gossipNetwork)
        .subscribe(argThat(i -> i.contains("beacon_attestation_" + subnetId1)), any());
    verify(gossipNetwork)
        .subscribe(argThat(i -> i.contains("beacon_attestation_" + subnetId2)), any());

    assertThat(subnetSubscriptions.getChannel(attestation1))
        .isCompletedWithValue(Optional.of(topicChannel1));
    assertThat(subnetSubscriptions.getChannel(attestation2))
        .isCompletedWithValue(Optional.of(topicChannel2));
  }

  @Test
  void shouldUnsubscribeFromOnlyCommitteeOnSubnet() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final int subnetId = computeSubnetId(attestation);
    TopicChannel topicChannel = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("beacon_attestation_" + subnetId), any()))
        .thenReturn(topicChannel);

    subnetSubscriptions.subscribeToSubnetId(subnetId);

    verify(gossipNetwork).subscribe(any(), any());

    assertThat(subnetSubscriptions.getChannel(attestation))
        .isCompletedWithValue(Optional.of(topicChannel));

    subnetSubscriptions.unsubscribeFromSubnetId(subnetId);

    verify(topicChannel).close();
  }

  private int computeSubnetId(final Attestation attestation) {
    return CommitteeUtil.computeSubnetForAttestation(
        recentChainData.getBestState().orElseThrow(), attestation);
  }
}
