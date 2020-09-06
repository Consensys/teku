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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.Snappy;
import tech.pegasys.teku.datastructures.util.LengthBounds;

/**
 * Implements snappy compression using the "block" format. See:
 * https://github.com/google/snappy/blob/master/format_description.txt
 */
public class SnappyBlockCompressor {

  public Bytes uncompress(final Bytes compressedData, final LengthBounds lengthBounds)
      throws DecodingException {

    try {
      final int actualLength = Snappy.uncompressedLength(compressedData.toArrayUnsafe());
      if (!lengthBounds.isWithinBounds(actualLength)) {
        throw new DecodingException(
            String.format(
                "Uncompressed length %d is not within expected bounds %d to %d",
                actualLength, lengthBounds.getMin(), lengthBounds.getMax()));
      }
      return Bytes.wrap(Snappy.uncompress(compressedData.toArrayUnsafe()));
    } catch (IOException e) {
      throw new DecodingException("Failed to uncompress", e);
    }
  }

  public Bytes compress(final Bytes data) {
    try {
      return Bytes.wrap(Snappy.compress(data.toArrayUnsafe()));
    } catch (IOException e) {
      throw new RuntimeException("Unable to compress data", e);
    }
  }
}
