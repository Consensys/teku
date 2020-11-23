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

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;

public abstract class AbstractGossipManager<T> {

  private final GossipEncoding gossipEncoding;
  private final GossipPublisher<T> publisher;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final TopicChannel channel;
  private final long subscriberId;

  protected AbstractGossipManager(
      final String topicName,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<T> processor,
      final GossipPublisher<T> publisher) {
    final Eth2TopicHandler<T> topicHandler =
        new Eth2TopicHandler<>(
            asyncRunner,
            processor,
            gossipEncoding,
            forkInfo.getForkDigest(),
            topicName,
            getGossipType());
    this.channel = gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
    this.gossipEncoding = gossipEncoding;
    this.publisher = publisher;

    this.subscriberId = publisher.subscribe(this::publishMessage);
  }

  protected abstract Class<T> getGossipType();

  protected void publishMessage(T message) {
    final Bytes data = gossipEncoding.encode(message);
    channel.gossip(data);
  }

  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      // Close gossip channels
      channel.close();
      publisher.unsubscribe(subscriberId);
    }
  }
}
