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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;

public class NoopCompressor implements Compressor {

  NoopFrameDecoder decoder;

  @Override
  public Bytes compress(final Bytes data) {
    return data;
  }

  @Override
  public Optional<ByteBuf> uncompress(ByteBuf input, int uncompressedPayloadSize)
      throws CompressionException {
    if (decoder == null) {
      decoder = new NoopFrameDecoder(uncompressedPayloadSize);
    } else if (uncompressedPayloadSize != decoder.getExpectedBytes()) {
      throw new CompressionException(
          "Illegal state: requesting "
              + uncompressedPayloadSize
              + " bytes while previous bytes of expected size "
              + decoder.getExpectedBytes()
              + " not yet returned");
    }
    Optional<ByteBuf> ret =
        uncompressedPayloadSize > 0
            ? decoder.decodeOneMessage(input)
            : Optional.of(Unpooled.EMPTY_BUFFER);

    if (ret.isPresent()) {
      decoder = null;
    }
    return ret;
  }

  @Override
  public int getMaxCompressedLength(final int uncompressedLength) {
    return uncompressedLength;
  }
}
