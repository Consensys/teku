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

import java.io.InputStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;

/**
 * Represents an rpc payload encoding where the header consists of a single protobuf varint holding
 * the length of the uncompressed payload
 */
public class LengthPrefixedEncoding implements RpcEncoding {
  private final String name;
  private final RpcPayloadEncoders payloadEncoders;
  private final Compressor compressor;

  LengthPrefixedEncoding(
      final String name, final RpcPayloadEncoders payloadEncoders, final Compressor compressor) {
    this.name = name;
    this.payloadEncoders = payloadEncoders;
    this.compressor = compressor;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Bytes encodePayload(final T message) {
    final RpcPayloadEncoder<T> payloadEncoder =
        payloadEncoders.getEncoder((Class<T>) message.getClass());
    final Bytes payload = payloadEncoder.encode(message);
    return encodeMessageWithLength(payload);
  }

  @Override
  public <T> T decodePayload(final InputStream inputStream, final Class<T> payloadType)
      throws RpcException {
    final LengthPrefixedPayloadDecoder<T> payloadDecoder =
        new LengthPrefixedPayloadDecoder<>(payloadEncoders.getEncoder(payloadType), compressor);
    return payloadDecoder.decodePayload(inputStream);
  }

  private Bytes encodeMessageWithLength(final Bytes payload) {
    final Bytes header = ProtobufEncoder.encodeVarInt(payload.size());
    final Bytes compressedPayload;
    compressedPayload = compressor.compress(payload);
    return Bytes.concatenate(header, compressedPayload);
  }

  @Override
  public String getName() {
    return name;
  }
}
