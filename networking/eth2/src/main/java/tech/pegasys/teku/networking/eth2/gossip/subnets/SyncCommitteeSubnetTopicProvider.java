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

import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics.getSyncCommitteeSubnetTopic;

import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeSubnetTopicProvider {
  private final RecentChainData recentChainData;
  private final GossipEncoding gossipEncoding;

  public SyncCommitteeSubnetTopicProvider(
      final RecentChainData recentChainData, final GossipEncoding gossipEncoding) {
    this.recentChainData = recentChainData;
    this.gossipEncoding = gossipEncoding;
  }

  public String getTopicForSubnet(final int subnetId) {
    final Bytes4 forkDigest = recentChainData.getCurrentForkInfo().orElseThrow().getForkDigest();
    return getSyncCommitteeSubnetTopic(forkDigest, subnetId, gossipEncoding);
  }
}
