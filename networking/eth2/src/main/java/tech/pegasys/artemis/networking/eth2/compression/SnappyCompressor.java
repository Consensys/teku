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

package tech.pegasys.artemis.networking.eth2.compression;

import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.SnappyFramedInputStream;
import org.xerial.snappy.SnappyFramedOutputStream;
import tech.pegasys.artemis.networking.eth2.compression.exceptions.CompressionException;
import tech.pegasys.artemis.networking.eth2.compression.exceptions.PayloadLargerThanExpectedException;
import tech.pegasys.artemis.networking.eth2.compression.exceptions.PayloadSmallerThanExpectedException;
import tech.pegasys.artemis.util.iostreams.DelegatingInputStream;

public class SnappyCompressor implements Compressor {
  // The max uncompressed bytes that will be packed into a single frame
  // See:
  // https://github.com/google/snappy/blob/251d935d5096da77c4fef26ea41b019430da5572/framing_format.txt#L104-L106
  static final int MAX_FRAME_CONTENT_SIZE = 65536;

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
  public Bytes uncompress(final InputStream input, final int uncompressedPayloadSize)
      throws CompressionException {
    // This is a bit of a hack - but we don't want to close the underlying stream when
    // we close the SnappyFramedInputStream
    final UncloseableInputStream unclosableStream = new UncloseableInputStream(input);
    // Limit the max number of bytes we're allowed to read
    final InputStream limitedStream =
        ByteStreams.limit(unclosableStream, getMaxCompressedLength(uncompressedPayloadSize));

    try (final InputStream snappyIn = new SnappyFramedInputStream(limitedStream)) {
      final Bytes uncompressed = Bytes.wrap(snappyIn.readNBytes(uncompressedPayloadSize));

      // Validate payload is of expected size
      if (uncompressed.size() < uncompressedPayloadSize) {
        throw new PayloadSmallerThanExpectedException(
            String.format(
                "Expected %d bytes but only uncompressed %d bytes",
                uncompressedPayloadSize, uncompressed.size()));
      }
      if (snappyIn.available() > 0) {
        throw new PayloadLargerThanExpectedException(
            String.format(
                "Expected %d bytes, but at least %d extra bytes are appended",
                uncompressedPayloadSize, snappyIn.available()));
      }

      return uncompressed;
    } catch (IOException e) {
      throw new CompressionException("Unable to uncompress data", e);
    }
  }

  @Override
  public int getMaxCompressedLength(final int uncompressedLength) {
    // Return worst-case compression size
    // See:
    // https://github.com/google/snappy/blob/537f4ad6240e586970fe554614542e9717df7902/snappy.cc#L98
    return 32 + uncompressedLength + uncompressedLength / 6;
  }

  private static class UncloseableInputStream extends DelegatingInputStream {

    public UncloseableInputStream(final InputStream wrapped) {
      super(wrapped);
    }

    @Override
    public void close() {
      // Don't close wrapped input stream
    }
  }
}
