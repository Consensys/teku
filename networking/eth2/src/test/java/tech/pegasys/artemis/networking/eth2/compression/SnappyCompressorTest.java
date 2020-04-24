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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.artemis.networking.eth2.compression.SnappyCompressor.MAX_FRAME_CONTENT_SIZE;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

public class SnappyCompressorTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Compressor compressor = new SnappyCompressor();

  @Test
  public void roundTrip() throws Exception {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState =
        Bytes.wrap(SimpleOffsetSerializer.serialize(state).toArrayUnsafe());

    final Bytes compressed = compressor.compress(serializedState);
    assertThat(compressed).isNotEqualTo(serializedState);

    final Bytes uncompressed = compressor.uncompress(compressed);
    assertThat(uncompressed).isEqualTo(serializedState);
  }

  @Test
  public void uncompress_invalidData() {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState =
        Bytes.wrap(SimpleOffsetSerializer.serialize(state).toArrayUnsafe());

    assertThatThrownBy(() -> compressor.uncompress(serializedState))
        .isInstanceOf(CompressionException.class);
  }

  @Test
  public void uncompress_seriesOfValues() throws Exception {
    final BeaconState stateA = dataStructureUtil.randomBeaconState(0);
    final BeaconState stateB = dataStructureUtil.randomBeaconState(1);
    final Bytes serializedStateA =
        Bytes.wrap(SimpleOffsetSerializer.serialize(stateA).toArrayUnsafe());
    final Bytes serializedStateB =
        Bytes.wrap(SimpleOffsetSerializer.serialize(stateB).toArrayUnsafe());

    final Bytes compressedA = compressor.compress(serializedStateA);
    final Bytes compressedB = compressor.compress(serializedStateB);
    final Bytes compressedSeries = Bytes.concatenate(compressedA, compressedB);
    final InputStream input = new ByteArrayInputStream(compressedSeries.toArrayUnsafe());

    // Get first value
    final Bytes uncompressed = compressor.uncompress(input, serializedStateA.size());
    assertThat(uncompressed).isEqualTo(serializedStateA);
    // Then next value
    final Bytes uncompressed2 = compressor.uncompress(input, serializedStateB.size());
    assertThat(uncompressed2).isEqualTo(serializedStateB);
    // Input stream should now be closed
    assertThat(input.available()).isEqualTo(0);
    assertThat(input.read()).isEqualTo(-1);
  }

  @Test
  public void uncompress_partialValue() throws Exception {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState =
        Bytes.wrap(SimpleOffsetSerializer.serialize(state).toArrayUnsafe());

    final Bytes compressed = compressor.compress(serializedState);
    final int maxBytes = MAX_FRAME_CONTENT_SIZE / 2;
    // Check assumptions
    assertThat(serializedState.size()).isGreaterThan(MAX_FRAME_CONTENT_SIZE);

    final InputStream input = new ByteArrayInputStream(compressed.toArrayUnsafe());
    final Bytes uncompressed = compressor.uncompress(input, maxBytes);
    assertThat(uncompressed.size()).isLessThanOrEqualTo(maxBytes);
  }

  @Test
  @Disabled("SnappyCompressor will currently block in this case")
  public void uncompress_partialValueWhenFullFrameUnavailable() throws Exception {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState =
        Bytes.wrap(SimpleOffsetSerializer.serialize(state).toArrayUnsafe());

    final Bytes compressed = compressor.compress(serializedState);
    final int partialPayloadSize = MAX_FRAME_CONTENT_SIZE / 2;
    final int bytesToRead = partialPayloadSize / 2;
    // Check assumptions
    assertThat(serializedState.size()).isGreaterThan(MAX_FRAME_CONTENT_SIZE);

    // Calculate the number of compressed bytes to request
    final int fullCapacity = Math.max(compressed.size(), serializedState.size());
    try (final PipedOutputStream outputStream = new PipedOutputStream();
        final InputStream inputStream = new PipedInputStream(outputStream, fullCapacity)) {
      outputStream.write(compressed.slice(0, partialPayloadSize).toArrayUnsafe());
      final Bytes result = compressor.uncompress(inputStream, bytesToRead);
      assertThat(result.size()).isLessThanOrEqualTo(bytesToRead);
    }
  }
}
