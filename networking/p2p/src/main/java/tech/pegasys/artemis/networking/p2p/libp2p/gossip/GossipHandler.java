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

import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.Unpooled;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.gossip.TopicHandler;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.collections.ConcurrentLimitedSet;
import tech.pegasys.artemis.util.collections.LimitStrategy;

public class GossipHandler implements Function<MessageApi, CompletableFuture<Boolean>> {
  private static final Logger LOG = LogManager.getLogger();

  private static SafeFuture<Boolean> VALIDATION_FAILED = SafeFuture.completedFuture(false);
  private static SafeFuture<Boolean> VALIDATION_SUCCEEDED = SafeFuture.completedFuture(true);
  static final int GOSSIP_MAX_SIZE = 1048576;
  private static final int MAX_SENT_MESSAGES = 2048;

  private final Topic topic;
  private final PubsubPublisherApi publisher;
  private final TopicHandler handler;
  private final Set<Bytes> processedMessages =
      ConcurrentLimitedSet.create(MAX_SENT_MESSAGES, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);

  public GossipHandler(
      final Topic topic, final PubsubPublisherApi publisher, final TopicHandler handler) {
    this.topic = topic;
    this.publisher = publisher;
    this.handler = handler;
  }

  @Override
  public SafeFuture<Boolean> apply(final MessageApi message) {
    final int messageSize = message.getData().capacity();
    if (messageSize > GOSSIP_MAX_SIZE) {
      LOG.trace(
          "Rejecting gossip message of length {} which exceeds maximum size of {}",
          messageSize,
          GOSSIP_MAX_SIZE);
      return VALIDATION_FAILED;
    }
    Bytes bytes = Bytes.wrapByteBuf(message.getData()).copy();
    if (!processedMessages.add(bytes)) {
      // We've already seen this message, skip processing
      LOG.trace("Ignoring duplicate message for topic {}: {} bytes", topic, bytes.size());
      return VALIDATION_FAILED;
    }
    LOG.trace("Received message for topic {}: {} bytes", topic, bytes.size());

    final boolean result = handler.handleMessage(bytes);
    return result ? VALIDATION_SUCCEEDED : VALIDATION_FAILED;
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
