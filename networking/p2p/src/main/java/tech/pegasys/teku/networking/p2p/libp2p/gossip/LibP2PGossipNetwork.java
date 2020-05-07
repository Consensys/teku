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
import io.libp2p.pubsub.gossip.Gossip;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;

public class LibP2PGossipNetwork implements tech.pegasys.teku.networking.p2p.gossip.GossipNetwork {
  private static final Logger LOG = LogManager.getLogger();

  private final Gossip gossip;
  private final PubsubPublisherApi publisher;

  public LibP2PGossipNetwork(final Gossip gossip, final PubsubPublisherApi publisher) {
    this.gossip = gossip;
    this.publisher = publisher;
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    LOG.trace("Subscribe to topic: {}", topic);
    final Topic libP2PTopic = new Topic(topic);
    final GossipHandler gossipHandler = new GossipHandler(libP2PTopic, publisher, topicHandler);
    PubsubSubscription subscription = gossip.subscribe(gossipHandler, libP2PTopic);
    return new LibP2PTopicChannel(gossipHandler, subscription);
  }
}
