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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.PubsubSubscription;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.etc.types.MultiSet;
import io.libp2p.etc.util.P2PService.PeerHandler;
import io.libp2p.pubsub.gossip.Gossip;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.util.async.SafeFuture;

public class LibP2PGossipNetwork implements tech.pegasys.teku.networking.p2p.gossip.GossipNetwork {
  private static final Logger LOG = LogManager.getLogger();

  private final Gossip gossip;
  private final PubsubPublisherApi publisher;

  public LibP2PGossipNetwork(final Gossip gossip, final PubsubPublisherApi publisher) {
    this.gossip = gossip;
    this.publisher = publisher;
  }

  @Override
  public SafeFuture<?> gossip(final String topic, final Bytes data) {
    return SafeFuture.of(
        publisher.publish(Unpooled.wrappedBuffer(data.toArrayUnsafe()), new Topic(topic)));
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    LOG.trace("Subscribe to topic: {}", topic);
    final Topic libP2PTopic = new Topic(topic);
    final GossipHandler gossipHandler = new GossipHandler(libP2PTopic, publisher, topicHandler);
    PubsubSubscription subscription = gossip.subscribe(gossipHandler, libP2PTopic);
    return new LibP2PTopicChannel(gossipHandler, subscription);
  }

  @Override
  public Map<String, Collection<NodeId>> getSubscribersByTopic() {
    return gossip
        .getRouter()
        .submitOnEventThread(
            () -> {
              final Map<String, Collection<NodeId>> result = new HashMap<>();
              final MultiSet<PeerHandler, String> peerTopics = gossip.getRouter().getPeerTopics();
              for (Map.Entry<? extends PeerHandler, ? extends List<String>> peerTopic :
                  peerTopics) {
                final LibP2PNodeId nodeId = new LibP2PNodeId(peerTopic.getKey().getPeerId());
                peerTopic
                    .getValue()
                    .forEach(
                        topic -> result.computeIfAbsent(topic, __ -> new HashSet<>()).add(nodeId));
              }
              return result;
            })
        .join();
  }
}
