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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.SnappyFramedOutputStream;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadLargerThanExpectedException;

/** Implements snappy compression using the "framed" / streaming format. */
public class SnappyFramedCompressor implements Compressor {
  // The max uncompressed bytes that will be packed into a single frame
  // See:
  // https://github.com/google/snappy/blob/251d935d5096da77c4fef26ea41b019430da5572/framing_format.txt#L104-L106
  static final int MAX_FRAME_CONTENT_SIZE = 65536;
  private SnappyFrameDecoder snappyFrameDecoder = new SnappyFrameDecoder();
  private List<ByteBuf> decodedSnappyFrames = new ArrayList<>();

  @Override
  public Bytes compress(final Bytes data) {

    try (final ByteArrayOutputStream out = new ByteArrayOutputStream(data.size() / 2);
        final OutputStream compressor = new SnappyFramedOutputStream(out)) {
      compressor.write(data.toArrayUnsafe());
      compressor.flush();
      return Bytes.wrap(out.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException("Failed to compress data", e);
    }
  }

  @Override
  public synchronized Optional<ByteBuf> uncompress(ByteBuf input, int uncompressedPayloadSize)
      throws CompressionException {
    while (true) {
      Optional<ByteBuf> byteBuf;
      try {
        byteBuf = snappyFrameDecoder.decodeOneMessage(input);
      } catch (Exception e) {
        throw new CompressionException("Error in Snappy decompressor", e);
      }
      if (byteBuf.isEmpty()) break;
      decodedSnappyFrames.add(byteBuf.get());
      int decodedFramesLength = decodedSnappyFrames.stream().mapToInt(ByteBuf::readableBytes).sum();
      if (decodedFramesLength == uncompressedPayloadSize) {
        // wrapped ByteBuf takes ownership of the underlying buffers
        ByteBuf ret = Unpooled.wrappedBuffer(decodedSnappyFrames.toArray(new ByteBuf[0]));
        decodedSnappyFrames.clear();
        snappyFrameDecoder.complete();
        return Optional.of(ret);
      } else if (decodedFramesLength > uncompressedPayloadSize) {
        throw new PayloadLargerThanExpectedException(
            "Decoded snappy frames len is "
                + decodedFramesLength
                + " while expecting "
                + uncompressedPayloadSize);
      }
    }

    return Optional.empty();
  }

  @Override
  public int getMaxCompressedLength(final int uncompressedLength) {
    // Return worst-case compression size
    // See:
    // https://github.com/google/snappy/blob/537f4ad6240e586970fe554614542e9717df7902/snappy.cc#L98
    return 32 + uncompressedLength + uncompressedLength / 6;
  }
}
