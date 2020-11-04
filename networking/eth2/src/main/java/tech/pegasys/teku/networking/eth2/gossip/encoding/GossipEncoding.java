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

public interface GossipEncoding {

  GossipEncoding SSZ_SNAPPY =
      new SszSnappyEncoding(new SszGossipEncoding(), new SnappyBlockCompressor());

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
  <T> Bytes encode(T value);

  /**
   * @param data Data received over gossip to be deserialized
   * @param valueType The concrete type to deserialize to
   * @return The deserialized value
   * @throws DecodingException If deserialization fails
   */
  <T> T decode(Bytes data, Class<T> valueType) throws DecodingException;
}
