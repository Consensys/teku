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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import com.google.common.eventbus.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.ValidationResult;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public abstract class Eth2TopicHandler<T extends SimpleOffsetSerializable> implements TopicHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final GossipEncoding gossipEncoding;
  private final Bytes4 forkDigest;
  protected final EventBus eventBus;

  protected Eth2TopicHandler(
      final GossipEncoding gossipEncoding, final ForkInfo forkInfo, final EventBus eventBus) {
    this.gossipEncoding = gossipEncoding;
    this.forkDigest = forkInfo.getForkDigest();
    this.eventBus = eventBus;
  }

  @Override
  public boolean handleMessage(final Bytes bytes) {
    T data;
    try {
      data = deserialize(bytes);
      final ValidationResult validationResult = validateData(data);
      switch (validationResult) {
        case INVALID:
          LOG.trace("Received invalid message for topic: {}", this::getTopic);
          return false;
        case SAVED_FOR_FUTURE:
          LOG.trace("Deferring message for topic: {}", this::getTopic);
          eventBus.post(createEvent(data));
          return false;
        case VALID:
          eventBus.post(createEvent(data));
          return true;
        default:
          throw new UnsupportedOperationException(
              "Unexpected validation result: " + validationResult);
      }
    } catch (DecodingException e) {
      LOG.trace("Received malformed gossip message on {}", getTopic());
      return false;
    } catch (Throwable e) {
      LOG.warn("Encountered exception while processing message for topic {}", getTopic(), e);
      return false;
    }
  }

  private T deserialize(Bytes bytes) throws DecodingException {
    return gossipEncoding.decode(bytes, getValueType());
  }

  protected Object createEvent(T data) {
    return data;
  }

  public String getTopic() {
    return "/eth2/"
        + forkDigest.toUnprefixedHexString()
        + "/"
        + getTopicName()
        + "/"
        + gossipEncoding.getName();
  }

  protected abstract String getTopicName();

  protected abstract Class<T> getValueType();

  protected abstract ValidationResult validateData(T dataObject);
}
