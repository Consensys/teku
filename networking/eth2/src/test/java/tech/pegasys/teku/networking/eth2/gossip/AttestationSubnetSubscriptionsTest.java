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

package tech.pegasys.teku.networking.eth2.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipedOperationConsumer;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.AttestationValidator;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationSubnetSubscriptionsTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private AttestationSubnetSubscriptions subnetSubscriptions;
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  @SuppressWarnings("unchecked")
  private final GossipedOperationConsumer<ValidateableAttestation> attestationConsumer =
      mock(GossipedOperationConsumer.class);

  @BeforeEach
  void setUp() {
    final RecentChainData recentChainData = mock(RecentChainData.class);
    when(recentChainData.getHeadForkInfo())
        .thenReturn(Optional.of(dataStructureUtil.randomForkInfo()));
    subnetSubscriptions =
        new AttestationSubnetSubscriptions(
            gossipNetwork,
            gossipEncoding,
            mock(AttestationValidator.class),
            recentChainData,
            attestationConsumer);

    when(gossipNetwork.subscribe(any(), any())).thenReturn(mock(TopicChannel.class));
  }

  @Test
  void getChannelReturnsEmptyIfNotSubscribedToSubnet() {
    int COMMITTEE_INDEX = 1;
    subnetSubscriptions.subscribeToSubnetId(COMMITTEE_INDEX);
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX)).isNotEqualTo(Optional.empty());
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX + 1)).isEqualTo(Optional.empty());
  }

  @Test
  void getChannelReturnsTheChannelFromSubnet() {
    int COMMITTEE_INDEX = 1;
    subnetSubscriptions.subscribeToSubnetId(COMMITTEE_INDEX);
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX)).isNotEqualTo(Optional.empty());
  }

  @Test
  void shouldSubscribeToCommitteesOnDifferentSubnets() {
    TopicChannel topicChannel1 = mock(TopicChannel.class);
    TopicChannel topicChannel2 = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("committee_index1"), any())).thenReturn(topicChannel1);
    when(gossipNetwork.subscribe(contains("committee_index2"), any())).thenReturn(topicChannel2);

    int COMMITTEE_INDEX_1 = 1;
    int COMMITTEE_INDEX_2 = 2;

    subnetSubscriptions.subscribeToSubnetId(COMMITTEE_INDEX_1);
    subnetSubscriptions.subscribeToSubnetId(COMMITTEE_INDEX_2);

    verifyNoInteractions(topicChannel2);

    verify(gossipNetwork).subscribe(argThat(i -> i.contains("committee_index1")), any());
    verify(gossipNetwork).subscribe(argThat(i -> i.contains("committee_index2")), any());

    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_1))
        .isEqualTo(Optional.of(topicChannel1));
    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_2))
        .isEqualTo(Optional.of(topicChannel2));
  }

  @Test
  void shouldUnsubscribeFromOnlyCommitteeOnSubnet() {
    TopicChannel topicChannel = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("committee_index1"), any())).thenReturn(topicChannel);

    int COMMITTEE_INDEX_1 = 1;

    subnetSubscriptions.subscribeToSubnetId(COMMITTEE_INDEX_1);

    verify(gossipNetwork).subscribe(any(), any());

    assertThat(subnetSubscriptions.getChannel(COMMITTEE_INDEX_1))
        .isEqualTo(Optional.of(topicChannel));

    subnetSubscriptions.unsubscribeFromSubnetId(COMMITTEE_INDEX_1);

    verify(topicChannel).close();
  }
}
