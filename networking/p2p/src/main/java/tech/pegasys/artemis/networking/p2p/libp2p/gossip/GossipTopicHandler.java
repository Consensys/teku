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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
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
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.collections.LimitedSet;
import tech.pegasys.artemis.util.collections.LimitedSet.Mode;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public abstract class GossipTopicHandler<T extends SimpleOffsetSerializable>
    implements Function<MessageApi, CompletableFuture<Boolean>> {
  private static final Logger LOG = LogManager.getLogger();
  
  private static CompletableFuture<Boolean> VALIDATION_FAILED =
      CompletableFuture.completedFuture(false);
  private static CompletableFuture<Boolean> VALIDATION_SUCCEEDED =
      CompletableFuture.completedFuture(true);
  static final int GOSSIP_MAX_SIZE = 1048576;
  private static final int MAX_SENT_MESSAGES = 2048;

  private final PubsubPublisherApi publisher;
  private final EventBus eventBus;

  private final Set<Bytes> processedMessages =
      LimitedSet.create(MAX_SENT_MESSAGES, Mode.DROP_LEAST_RECENTLY_ACCESSED);

  protected GossipTopicHandler(final PubsubPublisherApi publisher, final EventBus eventBus) {
    this.publisher = publisher;
    this.eventBus = eventBus;
  }

  public abstract Topic getTopic();

  @Override
  public final CompletableFuture<Boolean> apply(MessageApi message) {
    Bytes bytes = Bytes.wrapByteBuf(message.getData()).copy();
    if (bytes.size() > GOSSIP_MAX_SIZE) {
      LOG.trace(
          "Rejecting gossip message of length {} which exceeds maximum size of {}",
          bytes.size(),
          GOSSIP_MAX_SIZE);
      return VALIDATION_FAILED;
    }
    if (!processedMessages.add(bytes)) {
      // We've already seen this message, skip processing
      LOG.trace("Ignoring duplicate message for topic {}: {} bytes", getTopic(), bytes.size());
      return VALIDATION_FAILED;
    }
    LOG.trace("Received message for topic {}: {} bytes", getTopic(), bytes.size());

    T data;
    try {
      data = deserializeData(bytes);
      if (!validateData(data)) {
        LOG.trace("Received invalid message for topic: {}", getTopic());
        return VALIDATION_FAILED;
      }
    } catch (SSZException e) {
      LOG.trace("Received malformed gossip message on {}", getTopic());
      return VALIDATION_FAILED;
    } catch (Throwable e) {
      LOG.warn("Encountered exception while processing message for topic {}", getTopic(), e);
      return VALIDATION_FAILED;
    }

    // Post and re-gossip data on successful processing
    gossip(bytes);
    eventBus.post(data);
    return VALIDATION_SUCCEEDED;
  }

  private T deserializeData(Bytes bytes) throws SSZException {
    final T deserialized = deserialize(bytes);
    if (deserialized == null) {
      throw new SSZException("Unable to deserialize message for topic " + getTopic());
    }
    return deserialized;
  }

  protected abstract T deserialize(Bytes bytes) throws SSZException;

  protected abstract boolean validateData(T dataObject);

  @VisibleForTesting
  public final void gossip(final T data) {
    final Bytes bytes = SimpleOffsetSerializer.serialize(data);
    if (!processedMessages.add(bytes)) {
      // We've already gossiped this data
      return;
    }
    gossip(bytes);
  }

  private void gossip(Bytes bytes) {
    LOG.trace("Gossiping {}: {} bytes", getTopic(), bytes.size());
    publisher
        .publish(Unpooled.wrappedBuffer(bytes.toArrayUnsafe()), getTopic())
        .whenComplete(
            (res, err) -> {
              if (err != null) {
                LOG.debug("Failed to gossip message on {}", getTopic(), err);
                return;
              }
              LOG.trace("Successfully gossiped message on {}", getTopic());
            });
  }
}
