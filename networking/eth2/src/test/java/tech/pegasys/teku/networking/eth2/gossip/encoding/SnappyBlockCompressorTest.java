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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.LengthBounds;

public class SnappyBlockCompressorTest {
  private final SnappyBlockCompressor compressor = new SnappyBlockCompressor();

  @Test
  public void roundTrip() throws DecodingException {
    final Bytes original = Bytes.fromHexString("0x010203040506");

    final Bytes compressed = compressor.compress(original);
    assertThat(compressed).isNotEqualTo(original);
    final Bytes uncompressed = compressor.uncompress(compressed, new LengthBounds(0, 1000));

    assertThat(uncompressed).isEqualTo(original);
  }

  @Test
  public void uncompress_randomData() {
    final Bytes data = Bytes.fromHexString("0x0102");

    assertThatThrownBy(() -> compressor.uncompress(data, new LengthBounds(0, 1000)))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  void uncompress_uncompressedLengthLongerThanMaxLength() {
    final Bytes original = Bytes.fromHexString("0x010203040506");

    final Bytes compressed = compressor.compress(original);
    assertThatThrownBy(() -> compressor.uncompress(compressed, new LengthBounds(0, 4)))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  void uncompress_uncompressedLengthShorterThanMinLength() {
    final Bytes original = Bytes.fromHexString("0x010203040506");

    final Bytes compressed = compressor.compress(original);
    assertThatThrownBy(() -> compressor.uncompress(compressed, new LengthBounds(100, 200)))
        .isInstanceOf(DecodingException.class);
  }
}
