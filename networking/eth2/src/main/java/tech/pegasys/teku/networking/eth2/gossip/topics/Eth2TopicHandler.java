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

import io.libp2p.core.pubsub.ValidationResult;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public interface Eth2TopicHandler<T extends SimpleOffsetSerializable> extends TopicHandler {

  @Override
  ValidationResult handleMessage(final Bytes bytes);

  default T deserialize(Bytes bytes) throws DecodingException {
    return getGossipEncoding().decode(bytes, getValueType());
  }

  default String getTopic() {
    return "/eth2/"
        + getForkDigest().toUnprefixedHexString()
        + "/"
        + getTopicName()
        + "/"
        + getGossipEncoding().getName();
  }

  Bytes4 getForkDigest();

  GossipEncoding getGossipEncoding();

  String getTopicName();

  Class<T> getValueType();
}
