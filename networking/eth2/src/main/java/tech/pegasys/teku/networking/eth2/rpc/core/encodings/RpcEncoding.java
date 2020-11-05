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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyFramedCompressor;

public interface RpcEncoding {

  RpcEncoding SSZ_SNAPPY =
      new LengthPrefixedEncoding(
          "ssz_snappy", RpcPayloadEncoders.createSszEncoders(), new SnappyFramedCompressor());

  /**
   * Encodes a payload with its encoding-dependent header
   *
   * @param payload The payload to encode
   * @param <T> The type of payload
   * @return The encoded header and payload bytes
   */
  <T> Bytes encodePayload(T payload);

  /**
   * Creates a brand new disposable {@link RpcByteBufDecoder} instance
   *
   * @param <T> The type of payload to decode
   * @param payloadType The type of payload to decode
   */
  <T> RpcByteBufDecoder<T> createDecoder(final Class<T> payloadType);

  String getName();
}
