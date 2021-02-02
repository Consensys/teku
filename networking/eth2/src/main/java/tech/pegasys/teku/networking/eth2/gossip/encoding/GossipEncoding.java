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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public interface GossipEncoding {

  GossipEncoding SSZ_SNAPPY = new SszSnappyEncoding(new SnappyBlockCompressor());

  /**
   * Get the name of the encoding. This is the name included as part of gossip topic strings.
   *
   * @return The name of this encoding.
   */
  String getName();

  /**
   * Serialize a value for transmission over gossip.
   *
   * @param value The value to serialize.
   * @return The serialized bytes.
   */
  <T extends ViewRead> Bytes encode(T value);

  /**
   * Preprocess the raw Gossip message. The returned preprocessed message will be later passed to
   * {@link #decodeMessage(PreparedGossipMessage, ViewType)}
   *
   * <p>If there is a problem while preprocessing a message the error should be memorized and later
   * be thrown as {@link DecodingException} from {@link #decodeMessage(PreparedGossipMessage,
   * ViewType)}
   *
   * @param data Data received over gossip to be deserialized
   * @param valueType The concrete type to deserialize to
   */
  <T extends ViewRead> PreparedGossipMessage prepareMessage(Bytes data, ViewType<T> valueType);

  /**
   * Fallback for {@link #prepareMessage(Bytes, ViewType)} for the case when decoded {@code valueType}
   * is unknown
   *
   * @param data raw Gossip message data
   */
  PreparedGossipMessage prepareUnknownMessage(Bytes data);

  /**
   * Decodes preprocessed message
   *
   * @param message preprocessed raw bytes message returned earlier by {@link #prepareMessage(Bytes,
   *     ViewType)}
   * @param valueType The concrete type to deserialize to
   * @return The deserialized value
   * @throws DecodingException If deserialization fails
   */
  <T extends ViewRead> T decodeMessage(PreparedGossipMessage message, ViewType<T> valueType)
      throws DecodingException;
}
