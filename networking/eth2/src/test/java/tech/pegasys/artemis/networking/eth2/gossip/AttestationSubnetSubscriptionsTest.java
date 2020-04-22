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
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class AttestationSubnetSubscriptionsTest {
  private AttestationSubnetSubscriptions subnetSubscriptions;
  private GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private EventBus eventBus = mock(EventBus.class);

  @BeforeEach
  void setUp() {
    subnetSubscriptions =
        new AttestationSubnetSubscriptions(gossipNetwork, mock(RecentChainData.class), eventBus);

    when(gossipNetwork.subscribe(any(), any())).thenReturn(mock(TopicChannel.class));
  }

  @Test
  void getChannelReturnsEmptyIfNotSubscribedToSpecificCommittee() {
    UnsignedLong COMMITTEE_INDEX = UnsignedLong.ONE;
    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX);
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX)).isNotEqualTo(Optional.empty());
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX.plus(UnsignedLong.ONE)))
        .isEqualTo(Optional.empty());
  }

  @Test
  void getChannelReturnsEmptyIfNotSubscribedToSpecificCommitteeEvenIfSubscribedToSameSubnet() {
    UnsignedLong COMMITTEE_INDEX = UnsignedLong.ONE;
    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX);
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX)).isNotEqualTo(Optional.empty());
    assertThat(
            subnetSubscriptions.getChannel(COMMITTEE_INDEX.plus(valueOf(ATTESTATION_SUBNET_COUNT))))
        .isEqualTo(Optional.empty());
  }

  @Test
  void subscribeAndUnsubscribeCorrectTopics() {
    TopicChannel topicChannel1 = mock(TopicChannel.class);
    TopicChannel topicChannel2 = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(any(), any())).thenReturn(topicChannel1).thenReturn(topicChannel2);

    UnsignedLong COMMITTEE_INDEX_SUBNET1_1 = UnsignedLong.ONE;
    UnsignedLong COMMITTEE_INDEX_SUBNET1_2 =
        UnsignedLong.ONE.plus(UnsignedLong.valueOf(ATTESTATION_SUBNET_COUNT));
    UnsignedLong COMMITTEE_INDEX_3 = UnsignedLong.valueOf(3);

    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_SUBNET1_1);
    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_SUBNET1_2);

    verifyNoInteractions(topicChannel2);

    subnetSubscriptions.subscribeToCommitteeTopic(COMMITTEE_INDEX_3);

    verify(gossipNetwork).subscribe(argThat(i -> i.contains("committee_index1")), any());
    verify(gossipNetwork).subscribe(argThat(i -> i.contains("committee_index3")), any());

    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_SUBNET1_1).isPresent()).isTrue();
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_SUBNET1_2).isPresent()).isTrue();
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_3).isPresent()).isTrue();
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_3.plus(UnsignedLong.ONE)).isEmpty())
        .isTrue();

    subnetSubscriptions.unsubscribeFromCommitteeTopic(COMMITTEE_INDEX_SUBNET1_1);

    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_SUBNET1_1).isEmpty()).isTrue();
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_SUBNET1_2).isPresent()).isTrue();
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_3).isPresent()).isTrue();

    subnetSubscriptions.unsubscribeFromCommitteeTopic(COMMITTEE_INDEX_SUBNET1_2);

    verify(topicChannel1).close();
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_SUBNET1_2).isEmpty()).isTrue();
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_3).isPresent()).isTrue();

    subnetSubscriptions.unsubscribeFromCommitteeTopic(COMMITTEE_INDEX_3);
    verify(topicChannel2).close();
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_3).isEmpty()).isTrue();
  }
}
