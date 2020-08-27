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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy;

import static tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyUtil.validateChecksum;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.Snappy;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.AbstractByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadSmallerThanExpectedException;

/**
 * This class is mostly borrowed from the Netty implementation:
 * https://github.com/netty/netty/blob/4.1/codec/src/main/java/io/netty/handler/codec/compression/SnappyFrameDecoder.java
 *
 * <p>Uncompresses a {@link ByteBuf} encoded with the Snappy framing format.
 *
 * <p>See <a href="https://github.com/google/snappy/blob/master/framing_format.txt">Snappy framing
 * format</a>.
 */
public class SnappyFrameDecoder extends AbstractByteBufDecoder<ByteBuf, CompressionException> {

  private enum ChunkType {
    STREAM_IDENTIFIER,
    COMPRESSED_DATA,
    UNCOMPRESSED_DATA,
    RESERVED_UNSKIPPABLE,
    RESERVED_SKIPPABLE
  }

  private static final int SNAPPY_IDENTIFIER_LEN = 6;
  private static final int MAX_UNCOMPRESSED_DATA_SIZE = 65536 + 4;

  private final Snappy snappy = new Snappy();
  private final boolean validateChecksums;

  private boolean started;
  private boolean corrupted;

  /**
   * Creates a new snappy-framed decoder with validation of checksums turned on. To turn checksum
   * validation off, please use the alternate {@link #SnappyFrameDecoder(boolean)} constructor.
   */
  public SnappyFrameDecoder() {
    this(true);
  }

  /**
   * Creates a new snappy-framed decoder with validation of checksums as specified.
   *
   * @param validateChecksums If true, the checksum field will be validated against the actual
   *     uncompressed data, and if the checksums do not match, a suitable {@link
   *     CompressionException} will be thrown
   */
  public SnappyFrameDecoder(boolean validateChecksums) {
    this.validateChecksums = validateChecksums;
  }

  @Override
  protected Optional<ByteBuf> decodeOneImpl(ByteBuf in) throws CompressionException {
    if (corrupted) {
      in.skipBytes(in.readableBytes());
      return Optional.empty();
    }

    ByteBuf ret = null;

    try {
      int idx = in.readerIndex();
      final int inSize = in.readableBytes();
      if (inSize < 4) {
        // We need to be at least able to read the chunk type identifier (one byte),
        // and the length of the chunk (3 bytes) in order to proceed
        return Optional.empty();
      }

      final int chunkTypeVal = in.getUnsignedByte(idx);
      final ChunkType chunkType = mapChunkType((byte) chunkTypeVal);
      final int chunkLength = in.getUnsignedMediumLE(idx + 1);

      switch (chunkType) {
        case STREAM_IDENTIFIER:
          if (started) {
            throw new CompressionException("Extra Snappy stream identifier");
          }
          if (chunkLength != SNAPPY_IDENTIFIER_LEN) {
            throw new CompressionException(
                "Unexpected length of stream identifier: " + chunkLength);
          }

          if (inSize < 4 + SNAPPY_IDENTIFIER_LEN) {
            break;
          }

          in.skipBytes(4);
          int offset = in.readerIndex();
          in.skipBytes(SNAPPY_IDENTIFIER_LEN);

          checkByte(in.getByte(offset++), (byte) 's');
          checkByte(in.getByte(offset++), (byte) 'N');
          checkByte(in.getByte(offset++), (byte) 'a');
          checkByte(in.getByte(offset++), (byte) 'P');
          checkByte(in.getByte(offset++), (byte) 'p');
          checkByte(in.getByte(offset), (byte) 'Y');

          started = true;
          break;
        case RESERVED_SKIPPABLE:
          if (!started) {
            throw new CompressionException(
                "Received RESERVED_SKIPPABLE tag before STREAM_IDENTIFIER");
          }

          if (inSize < 4 + chunkLength) {
            return Optional.empty();
          }

          in.skipBytes(4 + chunkLength);
          break;
        case RESERVED_UNSKIPPABLE:
          // The spec mandates that reserved unskippable chunks must immediately
          // return an error, as we must assume that we cannot decode the stream
          // correctly
          throw new CompressionException(
              "Found reserved unskippable chunk type: 0x" + Integer.toHexString(chunkTypeVal));
        case UNCOMPRESSED_DATA:
          if (!started) {
            throw new CompressionException(
                "Received UNCOMPRESSED_DATA tag before STREAM_IDENTIFIER");
          }
          if (chunkLength > MAX_UNCOMPRESSED_DATA_SIZE) {
            throw new CompressionException("Received UNCOMPRESSED_DATA larger than 65540 bytes");
          }

          if (inSize < 4 + chunkLength) {
            return Optional.empty();
          }

          in.skipBytes(4);
          if (validateChecksums) {
            int checksum = in.readIntLE();
            validateChecksum(checksum, in, in.readerIndex(), chunkLength - 4);
          } else {
            in.skipBytes(4);
          }
          ret = in.readRetainedSlice(chunkLength - 4);
          break;
        case COMPRESSED_DATA:
          if (!started) {
            throw new CompressionException("Received COMPRESSED_DATA tag before STREAM_IDENTIFIER");
          }

          if (inSize < 4 + chunkLength) {
            return Optional.empty();
          }

          in.skipBytes(4);
          int checksum = in.readIntLE();
          ByteBuf uncompressed = Unpooled.buffer();
          try {
            if (validateChecksums) {
              int oldWriterIndex = in.writerIndex();
              try {
                in.writerIndex(in.readerIndex() + chunkLength - 4);
                snappy.decode(in, uncompressed);
              } finally {
                in.writerIndex(oldWriterIndex);
              }
              validateChecksum(checksum, uncompressed, 0, uncompressed.writerIndex());
            } else {
              snappy.decode(in.readSlice(chunkLength - 4), uncompressed);
            }
            ret = uncompressed;
            uncompressed = null;
          } finally {
            if (uncompressed != null) {
              uncompressed.release();
            }
          }
          snappy.reset();
          break;
      }
    } catch (CompressionException e) {
      corrupted = true;
      throw e;
    }
    return Optional.ofNullable(ret);
  }

  @Override
  protected void throwUnprocessedDataException(int dataLeft) throws CompressionException {
    throw new PayloadSmallerThanExpectedException(
        "Snappy stream complete, but unprocessed data left: " + dataLeft);
  }

  private static void checkByte(byte actual, byte expect) throws CompressionException {
    if (actual != expect) {
      throw new CompressionException(
          "Unexpected stream identifier contents. Mismatched snappy protocol version?");
    }
  }

  /**
   * Decodes the chunk type from the type tag byte.
   *
   * @param type The tag byte extracted from the stream
   * @return The appropriate {@link ChunkType}, defaulting to {@link ChunkType#RESERVED_UNSKIPPABLE}
   */
  private static ChunkType mapChunkType(byte type) {
    if (type == 0) {
      return ChunkType.COMPRESSED_DATA;
    } else if (type == 1) {
      return ChunkType.UNCOMPRESSED_DATA;
    } else if (type == (byte) 0xff) {
      return ChunkType.STREAM_IDENTIFIER;
    } else if ((type & 0x80) == 0x80) {
      return ChunkType.RESERVED_SKIPPABLE;
    } else {
      return ChunkType.RESERVED_UNSKIPPABLE;
    }
  }
}
