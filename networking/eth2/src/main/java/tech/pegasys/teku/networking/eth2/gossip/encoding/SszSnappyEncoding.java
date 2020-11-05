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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.util.LengthBounds;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;

class SszSnappyEncoding implements GossipEncoding {
  private static final String NAME = "ssz_snappy";
  private final SnappyBlockCompressor snappyCompressor;
  private final GossipEncoding sszEncoding;

  public SszSnappyEncoding(
      final GossipEncoding sszEncoding, final SnappyBlockCompressor snappyCompressor) {
    this.snappyCompressor = snappyCompressor;
    this.sszEncoding = sszEncoding;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public <T> Bytes encode(final T value) {
    return snappyCompressor.compress(sszEncoding.encode(value));
  }

  @Override
  public <T> T decode(Bytes uncompressedData, Class<T> valueType) throws DecodingException {
    return sszEncoding.decode(uncompressedData, valueType);
  }
}
