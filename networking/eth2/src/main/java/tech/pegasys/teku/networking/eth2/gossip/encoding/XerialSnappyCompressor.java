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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.Snappy;

/**
 * Implements snappy compression using snappy-java and the "block" format. See:
 * https://github.com/google/snappy/blob/master/format_description.txt
 */
public class XerialSnappyCompressor extends SnappyCompressor {

  @Override
  protected int getUncompressedLength(final byte[] compressedData) throws IOException {
    return Snappy.uncompressedLength(compressedData);
  }

  @Override
  protected Bytes uncompress(final byte[] compressedData, final int uncompressedLength)
      throws IOException {
    return Bytes.wrap(Snappy.uncompress(compressedData));
  }

  @Override
  protected Bytes compress(final byte[] data) throws IOException {
    return Bytes.wrap(Snappy.compress(data));
  }
}
