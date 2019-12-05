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

package tech.pegasys.artemis.networking.p2p.libp2p.gossip;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.PubsubSubscription;
import io.libp2p.pubsub.gossip.Gossip;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import tech.pegasys.artemis.statetransition.events.CommitteeAssignmentEvent;
import tech.pegasys.artemis.statetransition.events.CommitteeDismissalEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class GossipMessageHandler {

  private final Gossip gossip;
  private EventBus eventBus;
  private final List<GossipTopicHandler<?>> topicHandlers;
  private final PubsubPublisherApi publisher;
  private final ChainStorageClient chainStorageClient;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final Map<Integer, AttestationTopicHandler> attestationTopicHandlers = new HashMap<>();
  private final Map<Integer, PubsubSubscription> subscriptions = new HashMap<>();

  GossipMessageHandler(
      final ChainStorageClient chainStorageClient,
      final PubsubPublisherApi publisher,
      final Gossip gossip,
      final EventBus eventBus,
      final List<GossipTopicHandler<?>> topicHandlers) {
    this.chainStorageClient = chainStorageClient;
    this.publisher = publisher;
    this.gossip = gossip;
    this.eventBus = eventBus;
    this.topicHandlers = topicHandlers;
    this.eventBus.register(this);
  }

  public void start() {
    if (started.compareAndSet(false, true)) {
      topicHandlers.forEach(
          topicHandler -> {
            gossip.subscribe(topicHandler, topicHandler.getTopic());
            eventBus.register(topicHandler);
          });
    }
  }

  public static GossipMessageHandler create(
      final Gossip gossip,
      final PrivKey privateKey,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient) {
    final PubsubPublisherApi publisher = createPublisher(gossip, privateKey);
    final List<GossipTopicHandler<?>> topicHandlers =
        createDefaultTopicHandlers(publisher, eventBus, chainStorageClient);
    return new GossipMessageHandler(chainStorageClient, publisher, gossip, eventBus, topicHandlers);
  }

  static PubsubPublisherApi createPublisher(final Gossip gossip, final PrivKey privateKey) {
    return gossip.createPublisher(privateKey, new Random().nextLong());
  }

  private static List<GossipTopicHandler<?>> createDefaultTopicHandlers(
      final PubsubPublisherApi publisher,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient) {
    return List.of(
        new AggregateTopicHandler(publisher, eventBus, chainStorageClient),
        new BlocksTopicHandler(publisher, eventBus, chainStorageClient));
  }

  @Subscribe
  public synchronized void registerAttestationTopicHandlers(
      CommitteeAssignmentEvent assignmentEvent) {
    List<Integer> committeeIndices = assignmentEvent.getCommitteeIndices();
    for (int committeeIndex : committeeIndices) {
      if (!attestationTopicHandlers.containsKey(committeeIndex)) {
        AttestationTopicHandler topicHandler =
            new AttestationTopicHandler(publisher, eventBus, chainStorageClient, committeeIndex);
        attestationTopicHandlers.put(committeeIndex, topicHandler);
        PubsubSubscription subscription = gossip.subscribe(topicHandler, topicHandler.getTopic());
        subscriptions.put(committeeIndex, subscription);
        eventBus.register(topicHandler);
      }
    }
  }

  @Subscribe
  public synchronized void unregisterAttestationTopicHandlers(
      CommitteeDismissalEvent dismissalEvent) {
    List<Integer> committeeIndices = dismissalEvent.getCommitteeIndices();
    for (int committeeIndex : committeeIndices) {
      if (attestationTopicHandlers.containsKey(committeeIndex)) {
        AttestationTopicHandler topicHandler = attestationTopicHandlers.remove(committeeIndex);
        PubsubSubscription subscription = subscriptions.remove(committeeIndex);
        subscription.unsubscribe();
        eventBus.unregister(topicHandler);
      }
    }
  }
}
