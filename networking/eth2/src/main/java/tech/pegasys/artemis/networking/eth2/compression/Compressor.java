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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.eth2.compression.exceptions.CompressionException;

public interface Compressor {

  /**
   * Returns the compressed data
   *
   * @param data The data to compress
   * @return The compressed data
   */
  Bytes compress(final Bytes data);

  /**
   * Returns the uncompressed data.
   *
   * @param data The data to uncompress.
   * @param uncompressedPayloadSize The expected size of the uncompressed payload
   * @return The uncompressed data.
   */
  default Bytes uncompress(final Bytes data, final int uncompressedPayloadSize)
      throws CompressionException {
    try (final InputStream byteStream = new ByteArrayInputStream(data.toArrayUnsafe())) {
      // Read everything
      return uncompress(byteStream, uncompressedPayloadSize);
    } catch (IOException e) {
      throw new RuntimeException(
          "Unexpected error encountered while preparing to uncompress bytes", e);
    }
  }

  /**
   * Uncompress a value expected to be {@code uncompressedPayloadSize} bytes.
   *
   * @param input The underlying {@link InputStream} to read from.
   * @param uncompressedPayloadSize The expected size of the uncompressed payload
   * @return The uncompressed bytes read from the underlying inputStream {@code input} stream.
   */
  Bytes uncompress(final InputStream input, final int uncompressedPayloadSize)
      throws CompressionException;

  /**
   * Returns a maximum estimate of the size of a compressed payload given the uncompressed payload
   * size.
   *
   * @param uncompressedLength The size of an uncompressed payload.
   * @return The maximum size of a payload of size {@code uncompressedLength} after compression.
   */
  int getMaxCompressedLength(final int uncompressedLength);
}
