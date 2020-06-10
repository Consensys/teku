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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import static tech.pegasys.teku.util.config.Constants.MAX_CHUNK_SIZE;

import io.libp2p.etc.types.ByteBufExtKt;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;

class LengthPrefixedPayloadDecoder<T> implements RpcByteBufDecoder<T>{

  private static class VarIntDecoder extends AbstractRpcByteBufDecoder<Long> {
    @Override
    protected Optional<Long> decodeOneImpl(ByteBuf in) {
      long length = ByteBufExtKt.readUvarint(in);
      if (length < 0) {
        // wait for more byte to read length field
        return Optional.empty();
      }
      return Optional.of(length);
    }
  }

  private final RpcPayloadEncoder<T> payloadEncoder;
  private final Compressor compressor;
  private Optional<Integer> payloadLength = Optional.empty();
  private VarIntDecoder varIntDecoder = null;

  public LengthPrefixedPayloadDecoder(
      final RpcPayloadEncoder<T> payloadEncoder, final Compressor compressor) {
    this.payloadEncoder = payloadEncoder;
    this.compressor = compressor;
  }

  @Override
  public Optional<T> decodeOneMessage(final ByteBuf in) throws RpcException {
    if (payloadLength.isEmpty()) {
      payloadLength = readLengthPrefixHeader(in);
    }
    if (payloadLength.isPresent()) {
      Optional<ByteBuf> ret = processPayload(in, payloadLength.get());
      if (ret.isPresent()) {
        payloadLength = Optional.empty();
        return Optional.of(payloadEncoder.decode(Bytes.wrapByteBuf(ret.get())));
      } else {
        return Optional.empty();
      }
    } else {
      return Optional.empty();
    }
  }

  @Override
  public void complete() throws RpcException {
    if (varIntDecoder != null) {
      varIntDecoder.complete();
    }
    if (payloadLength.isPresent()) {
      // the chunk was not completely read
      throw RpcException.PAYLOAD_TRUNCATED();
    }
  }

  /** Decode the length-prefix header, which contains the length of the uncompressed payload */
  private Optional<Integer> readLengthPrefixHeader(final ByteBuf in)
      throws RpcException {

    if (varIntDecoder == null) {
      varIntDecoder = new VarIntDecoder();
    }

    Optional<Long> lengthMaybe = varIntDecoder.decodeOneMessage(in);
    if (lengthMaybe.isEmpty()) {
      // wait for more byte to read length field
      return Optional.empty();
    }

    varIntDecoder = null;

    long length = lengthMaybe.get();
    if (length > MAX_CHUNK_SIZE) {
      throw RpcException.CHUNK_TOO_LONG;
    }
    return Optional.of((int) length);
  }

  private Optional<ByteBuf> processPayload(final ByteBuf input, final int uncompressedPayloadSize)
      throws RpcException, CompressionException {
    Optional<ByteBuf> uncompressedPayloadMaybe = compressor.uncompress(input, uncompressedPayloadSize);
    if (uncompressedPayloadMaybe.isEmpty()) {
      return Optional.empty();
    } else {
      ByteBuf uncompressedPayload = uncompressedPayloadMaybe.get();
      if (uncompressedPayload.readableBytes() < uncompressedPayloadSize) {
        throw RpcException.PAYLOAD_TRUNCATED();
      }
      return Optional.of(uncompressedPayload);
      }
  }
}
