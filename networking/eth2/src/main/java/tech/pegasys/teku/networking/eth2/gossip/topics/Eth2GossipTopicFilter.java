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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics.getAllTopics;

import com.google.common.base.Suppliers;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class Eth2GossipTopicFilter implements GossipTopicFilter {
  private static final Logger LOG = LogManager.getLogger();
  private final Supplier<Set<String>> relevantTopics;
  private final Spec spec;

  public Eth2GossipTopicFilter(
      final RecentChainData recentChainData, final GossipEncoding gossipEncoding, final Spec spec) {
    this.spec = spec;
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
    final ForkInfo forkInfo = recentChainData.getCurrentForkInfo().orElseThrow();
    final Bytes4 forkDigest = forkInfo.getForkDigest(spec);
    final Set<String> topics = getAllTopics(gossipEncoding, forkDigest, spec);
    spec.getForkSchedule().getForks().stream()
        .filter(fork -> fork.getEpoch().isGreaterThanOrEqualTo(forkInfo.getFork().getEpoch()))
        .forEach(
            futureFork -> {
              final Bytes4 futureForkDigest =
                  spec.atEpoch(futureFork.getEpoch())
                      .miscHelpers()
                      .computeForkDigest(
                          futureFork.getCurrentVersion(), forkInfo.getGenesisValidatorsRoot());
              topics.addAll(getAllTopics(gossipEncoding, futureForkDigest, spec));
            });
    return topics;
  }
}
