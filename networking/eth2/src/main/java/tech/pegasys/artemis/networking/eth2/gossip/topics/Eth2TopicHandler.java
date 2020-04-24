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

package tech.pegasys.artemis.networking.eth2.gossip.topics;

import com.google.common.eventbus.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.artemis.networking.p2p.gossip.TopicHandler;
import tech.pegasys.artemis.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.artemis.storage.client.RecentChainData;

public abstract class Eth2TopicHandler<T extends SimpleOffsetSerializable> implements TopicHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  protected final RecentChainData recentChainData;

  protected Eth2TopicHandler(final EventBus eventBus, final RecentChainData recentChainData) {
    this.eventBus = eventBus;
    this.recentChainData = recentChainData;
  }

  @Override
  public boolean handleMessage(final Bytes bytes) {
    T data;
    try {
      data = deserializeData(bytes);
      if (!validateData(data)) {
        LOG.trace("Received invalid message for topic: {}", getTopic());
        return false;
      }
    } catch (SSZException e) {
      LOG.trace("Received malformed gossip message on {}", getTopic());
      return false;
    } catch (Throwable e) {
      LOG.warn("Encountered exception while processing message for topic {}", getTopic(), e);
      return false;
    }

    eventBus.post(createEvent(data));
    return true;
  }

  protected Object createEvent(T data) {
    return data;
  }

  public String getTopic() {
    return "/eth2/" + getForkDigestValue() + "/" + getTopicName() + "/ssz";
  }

  private String getForkDigestValue() {
    return recentChainData.getCurrentForkDigest().toHexString().substring(2);
  }

  protected abstract T deserialize(Bytes bytes) throws SSZException;

  protected abstract boolean validateData(T dataObject);

  private T deserializeData(Bytes bytes) throws SSZException {
    final T deserialized = deserialize(bytes);
    if (deserialized == null) {
      throw new SSZException("Unable to deserialize message for topic " + getTopic());
    }
    return deserialized;
  }
}
