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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.SnappyFramedInputStream;
import org.xerial.snappy.SnappyFramedOutputStream;

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
  public Bytes uncompress(final InputStream input, final int maxBytes) throws CompressionException {
    try (final InputStream snappyIn = new SnappyFramedInputStream(input)) {
      return Bytes.wrap(snappyIn.readNBytes(maxBytes));
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
}
