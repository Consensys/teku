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

import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.pubsub.PubsubMessage;
import io.netty.buffer.Unpooled;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;

public class GossipHandler implements Function<MessageApi, CompletableFuture<ValidationResult>> {
  private static final Logger LOG = LogManager.getLogger();

  private static final SafeFuture<ValidationResult> VALIDATION_FAILED =
      SafeFuture.completedFuture(ValidationResult.Invalid);
  private static final SafeFuture<ValidationResult> VALIDATION_IGNORED =
      SafeFuture.completedFuture(ValidationResult.Ignore);

  private static final int MAX_SENT_MESSAGES = 2048;

  private final Topic topic;
  private final PubsubPublisherApi publisher;
  private final TopicHandler handler;
  private final Set<Bytes> processedMessages = LimitedSet.create(MAX_SENT_MESSAGES);
  private final Counter messageCounter;

  public GossipHandler(
      final MetricsSystem metricsSystem,
      final Topic topic,
      final PubsubPublisherApi publisher,
      final TopicHandler handler) {
    this.topic = topic;
    this.publisher = publisher;
    this.handler = handler;
    this.messageCounter =
        metricsSystem
            .createLabelledCounter(
                TekuMetricCategory.LIBP2P,
                "gossip_messages_total",
                "Total number of gossip messages received (avoid libp2p deduplication)",
                "topic")
            .labels(topic.getTopic());
  }

  @Override
  public SafeFuture<ValidationResult> apply(final MessageApi message) {
    messageCounter.inc();
    final int messageSize = message.getData().readableBytes();
    final int maxMessageSize = handler.getMaxMessageSize();
    if (messageSize > maxMessageSize) {
      LOG.trace(
          "Rejecting gossip message of length {} which exceeds maximum size of {}",
          messageSize,
          maxMessageSize);
      return VALIDATION_FAILED;
    }
    byte[] arr = new byte[message.getData().readableBytes()];
    message.getData().slice().readBytes(arr);
    Bytes bytes = Bytes.wrap(arr);
    if (!processedMessages.add(bytes)) {
      // We've already seen this message, skip processing
      LOG.trace("Ignoring duplicate message for topic {}: {} bytes", topic, bytes.size());
      return VALIDATION_IGNORED;
    }
    LOG.trace("Received message for topic {}: {} bytes", topic, bytes.size());

    PubsubMessage pubsubMessage = message.getOriginalMessage();
    if (!(pubsubMessage instanceof PreparedPubsubMessage)) {
      throw new IllegalArgumentException(
          "Don't know this PubsubMessage implementation: " + pubsubMessage.getClass());
    }
    PreparedPubsubMessage gossipPubsubMessage = (PreparedPubsubMessage) pubsubMessage;
    return handler.handleMessage(gossipPubsubMessage.getPreparedMessage());
  }

  public void gossip(Bytes bytes) {
    if (!processedMessages.add(bytes)) {
      // We've already gossiped this data
      return;
    }

    LOG.trace("Gossiping {}: {} bytes", topic, bytes.size());
    SafeFuture.of(publisher.publish(Unpooled.wrappedBuffer(bytes.toArrayUnsafe()), topic))
        .finish(
            () -> LOG.trace("Successfully gossiped message on {}", topic),
            err -> LOG.debug("Failed to gossip message on " + topic, err));
  }
}
