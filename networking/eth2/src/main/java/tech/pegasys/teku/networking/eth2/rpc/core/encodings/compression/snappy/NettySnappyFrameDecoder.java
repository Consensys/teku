/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.Snappy;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;

/** Snappy framed decoder that uses Netty for raw snappy chunks. */
public class NettySnappyFrameDecoder extends SnappyFrameDecoder {

  // Owned by a fresh frame decoder per RPC payload; this mutable codec is not shared.
  private final Snappy snappy = new Snappy();

  @Override
  protected ByteBuf decodeCompressedData(final ByteBuf input, final int compressedLength)
      throws CompressionException {
    final ByteBuf uncompressed = Unpooled.buffer(compressedLength, MAX_DECOMPRESSED_DATA_SIZE);
    try {
      final int oldWriterIndex = input.writerIndex();
      try {
        input.writerIndex(input.readerIndex() + compressedLength);
        snappy.decode(input, uncompressed);
      } finally {
        input.writerIndex(oldWriterIndex);
      }
      return uncompressed;
    } catch (final RuntimeException e) {
      uncompressed.release();
      throw e;
    }
  }

  @Override
  protected void reset() {
    snappy.reset();
  }
}
