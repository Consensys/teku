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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_fork_digest;
import static tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding.SSZ_SNAPPY;
import static tech.pegasys.teku.networking.eth2.gossip.topics.TopicNames.getAttestationSubnetTopicName;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

class Eth2GossipTopicFilterTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final Fork nextFork = dataStructureUtil.randomFork();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final Bytes4 nextForkDigest =
      compute_fork_digest(nextFork.getCurrent_version(), forkInfo.getGenesisValidatorsRoot());

  private final Eth2GossipTopicFilter filter =
      new Eth2GossipTopicFilter(recentChainData, SSZ_SNAPPY);

  @BeforeEach
  void setUp() {
    when(recentChainData.getHeadForkInfo()).thenReturn(Optional.of(forkInfo));
    when(recentChainData.getNextFork()).thenReturn(Optional.of(nextFork));
  }

  @Test
  void shouldNotAllowIrrelevantTopics() {
    assertThat(filter.isRelevantTopic("abc")).isFalse();
  }

  @Test
  void shouldNotRequireNextForkToBePresent() {
    when(recentChainData.getNextFork()).thenReturn(Optional.empty());
    assertThat(filter.isRelevantTopic(getTopicName(BlockGossipManager.TOPIC_NAME))).isTrue();
  }

  @Test
  void shouldConsiderTopicsForNextForkRelevant() {
    assertThat(filter.isRelevantTopic(getNextForkTopicName(BlockGossipManager.TOPIC_NAME)))
        .isTrue();
  }

  @Test
  void shouldConsiderAllAttestationSubnetsRelevant() {
    for (int i = 0; i < Constants.ATTESTATION_SUBNET_COUNT; i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getAttestationSubnetTopicName(i)))).isTrue();
      assertThat(filter.isRelevantTopic(getNextForkTopicName(getAttestationSubnetTopicName(i))))
          .isTrue();
    }
  }

  @Test
  void shouldNotAllowTopicsWithUnknownForkDigest() {
    final String irrelevantTopic =
        TopicNames.getTopic(
            Bytes4.fromHexString("0x11223344"), BlockGossipManager.TOPIC_NAME, SSZ_SNAPPY);
    assertThat(filter.isRelevantTopic(irrelevantTopic)).isFalse();
  }

  private String getTopicName(final String name) {
    return TopicNames.getTopic(forkInfo.getForkDigest(), name, SSZ_SNAPPY);
  }

  private String getNextForkTopicName(final String name) {
    return TopicNames.getTopic(nextForkDigest, name, SSZ_SNAPPY);
  }
}
