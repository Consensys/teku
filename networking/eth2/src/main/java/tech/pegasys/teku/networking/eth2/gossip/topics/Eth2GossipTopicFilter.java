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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_fork_digest;
import static tech.pegasys.teku.networking.eth2.gossip.topics.TopicNames.getAttestationSubnetTopic;

import com.google.common.base.Suppliers;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttesterSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ProposerSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.VoluntaryExitGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class Eth2GossipTopicFilter implements GossipTopicFilter {
  private static final Logger LOG = LogManager.getLogger();
  private final Supplier<Set<String>> relevantTopics;

  public Eth2GossipTopicFilter(
      final RecentChainData recentChainData, final GossipEncoding gossipEncoding) {
    relevantTopics =
        Suppliers.memoize(() -> computeRelevantTopics(recentChainData, gossipEncoding));
  }

  @Override
  public boolean isRelevantTopic(final String topic) {
    final boolean allowed = relevantTopics.get().contains(topic);
    if (!allowed) {
      LOG.debug("Ignoring subscription request for topic {}", topic);
    }
    return allowed;
  }

  private Set<String> computeRelevantTopics(
      final RecentChainData recentChainData, final GossipEncoding gossipEncoding) {
    final ForkInfo forkInfo = recentChainData.getHeadForkInfo().orElseThrow();
    final Bytes4 forkDigest = forkInfo.getForkDigest();
    final Set<String> topics = new HashSet<>();
    addTopicsForForkDigest(gossipEncoding, forkDigest, topics);
    recentChainData
        .getNextFork()
        .map(
            nextFork ->
                compute_fork_digest(
                    nextFork.getCurrent_version(), forkInfo.getGenesisValidatorsRoot()))
        .ifPresent(
            nextForkDigest -> addTopicsForForkDigest(gossipEncoding, nextForkDigest, topics));
    return topics;
  }

  private void addTopicsForForkDigest(
      final GossipEncoding gossipEncoding, final Bytes4 forkDigest, final Set<String> topics) {
    for (int i = 0; i < Constants.ATTESTATION_SUBNET_COUNT; i++) {
      topics.add(getAttestationSubnetTopic(forkDigest, i, gossipEncoding));
    }

    for (String topicName :
        List.of(
            BlockGossipManager.TOPIC_NAME,
            AggregateGossipManager.TOPIC_NAME,
            AttesterSlashingGossipManager.TOPIC_NAME,
            ProposerSlashingGossipManager.TOPIC_NAME,
            VoluntaryExitGossipManager.TOPIC_NAME)) {
      topics.add(TopicNames.getTopic(forkDigest, topicName, gossipEncoding));
    }
  }
}
