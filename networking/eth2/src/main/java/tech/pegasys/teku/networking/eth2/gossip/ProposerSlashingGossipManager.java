/*
 * Copyright 2019 ConsenSys AG.
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

import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;

import java.util.concurrent.atomic.AtomicBoolean;

public class ProposerSlashingGossipManager {
  private final TopicChannel channel;
  public static String TOPIC_NAME = "proposer_slashing";

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public ProposerSlashingGossipManager(
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<ProposerSlashing> processor) {
    final Eth2TopicHandler<ProposerSlashing> topicHandler =
        new Eth2TopicHandler<>(
            asyncRunner, processor, gossipEncoding, forkInfo.getForkDigest(), TOPIC_NAME, ProposerSlashing.class);
    this.channel = gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      // Close gossip channels
      channel.close();
    }
  }
}
