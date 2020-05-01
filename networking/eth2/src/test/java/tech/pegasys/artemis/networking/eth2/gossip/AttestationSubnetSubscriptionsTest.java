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

package tech.pegasys.artemis.networking.eth2.gossip;

import static com.google.common.primitives.UnsignedLong.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class AttestationSubnetSubscriptionsTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private AttestationSubnetSubscriptions subnetSubscriptions;
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final EventBus eventBus = mock(EventBus.class);

  @BeforeEach
  void setUp() {
    final RecentChainData recentChainData = mock(RecentChainData.class);
    when(recentChainData.getCurrentForkInfo())
        .thenReturn(Optional.of(dataStructureUtil.randomForkInfo()));
    subnetSubscriptions =
        new AttestationSubnetSubscriptions(
            gossipNetwork, recentChainData, mock(AttestationValidator.class), eventBus);

    when(gossipNetwork.subscribe(any(), any())).thenReturn(mock(TopicChannel.class));
  }

  @Test
  void getChannelReturnsEmptyIfNotSubscribedToSubnet() {
    UnsignedLong COMMITTEE_INDEX = UnsignedLong.ONE;
    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX);
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX)).isNotEqualTo(Optional.empty());
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX.plus(UnsignedLong.ONE)))
        .isEqualTo(Optional.empty());
  }

  @Test
  void getChannelReturnsTheChannelFromSameSubnetEvenIfNotSubscribedToSpecificCommittee() {
    UnsignedLong COMMITTEE_INDEX = UnsignedLong.ONE;
    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX);
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX)).isNotEqualTo(Optional.empty());
    assertThat(
            subnetSubscriptions.getChannel(COMMITTEE_INDEX.plus(valueOf(ATTESTATION_SUBNET_COUNT))))
        .isNotEqualTo(Optional.empty());
  }

  @Test
  void shouldSubscribeToCommitteesOnDifferentSubnets() {
    TopicChannel topicChannel1 = mock(TopicChannel.class);
    TopicChannel topicChannel2 = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("committee_index1"), any())).thenReturn(topicChannel1);
    when(gossipNetwork.subscribe(contains("committee_index2"), any())).thenReturn(topicChannel2);

    UnsignedLong COMMITTEE_INDEX_1 = UnsignedLong.ONE;
    UnsignedLong COMMITTEE_INDEX_2 = UnsignedLong.valueOf(2);

    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_1);
    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_2);

    verifyNoInteractions(topicChannel2);

    verify(gossipNetwork).subscribe(argThat(i -> i.contains("committee_index1")), any());
    verify(gossipNetwork).subscribe(argThat(i -> i.contains("committee_index2")), any());

    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_1))
        .isEqualTo(Optional.of(topicChannel1));
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_2))
        .isEqualTo(Optional.of(topicChannel2));
  }

  @Test
  void shouldSubscribeToCommitteesOnSameSubnet() {
    TopicChannel topicChannel = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("committee_index1"), any())).thenReturn(topicChannel);

    UnsignedLong COMMITTEE_INDEX_1 = UnsignedLong.ONE;
    UnsignedLong COMMITTEE_INDEX_65 = UnsignedLong.valueOf(65);

    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_1);
    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_65);

    verify(gossipNetwork).subscribe(any(), any());

    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_1))
        .isEqualTo(Optional.of(topicChannel));
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_65))
        .isEqualTo(Optional.of(topicChannel));
  }

  @Test
  void shouldUnsubscribeFromOnlyCommitteeOnSubnet() {
    TopicChannel topicChannel = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("committee_index1"), any())).thenReturn(topicChannel);

    UnsignedLong COMMITTEE_INDEX_1 = UnsignedLong.ONE;

    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_1);

    verify(gossipNetwork).subscribe(any(), any());

    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_1))
        .isEqualTo(Optional.of(topicChannel));

    subnetSubscriptions.unsubscribeFromCommitteeTopic(COMMITTEE_INDEX_1);

    verify(topicChannel).close();
  }

  @Test
  void shouldNotUnsubscribeFromSubnetWhenOtherCommitteesStillRequireIt() {
    TopicChannel topicChannel = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("committee_index1"), any())).thenReturn(topicChannel);

    UnsignedLong COMMITTEE_INDEX_1 = UnsignedLong.ONE;
    UnsignedLong COMMITTEE_INDEX_65 = UnsignedLong.valueOf(65);

    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_1);
    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_65);

    verify(gossipNetwork).subscribe(any(), any());

    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_1))
        .isEqualTo(Optional.of(topicChannel));
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_65))
        .isEqualTo(Optional.of(topicChannel));

    subnetSubscriptions.unsubscribeFromCommitteeTopic(COMMITTEE_INDEX_1);

    verifyNoInteractions(topicChannel);
  }
}
