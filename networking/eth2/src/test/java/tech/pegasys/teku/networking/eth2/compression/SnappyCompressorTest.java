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

package tech.pegasys.teku.networking.eth2.compression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.networking.eth2.compression.SnappyCompressor.MAX_FRAME_CONTENT_SIZE;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.eth2.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.compression.exceptions.PayloadLargerThanExpectedException;
import tech.pegasys.teku.networking.eth2.compression.exceptions.PayloadSmallerThanExpectedException;

public class SnappyCompressorTest {
  // The static snappy header taken from the Snappy library
  // see:
  // https://github.com/xerial/snappy-java/blob/de99182a82516c60d29813820926003b2543faf5/src/main/java/org/xerial/snappy/SnappyFramed.java#L121
  private static final Bytes SNAPPY_HEADER =
      Bytes.wrap(new byte[] {(byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59});

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
  public void uncompress_truncatedPayload() {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState =
        Bytes.wrap(SimpleOffsetSerializer.serialize(state).toArrayUnsafe());

    // Compress and deliver only part of the payload
    final int payloadSize = serializedState.size();
    final Bytes compressed = compressor.compress(serializedState.slice(1));

    final InputStream input = new ByteArrayInputStream(compressed.toArrayUnsafe());
    assertThatThrownBy(() -> compressor.uncompress(input, payloadSize))
        .isInstanceOf(PayloadSmallerThanExpectedException.class);
  }

  @Test
  public void uncompress_appendExtraDataToPayload() {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState =
        Bytes.wrap(SimpleOffsetSerializer.serialize(state).toArrayUnsafe());

    // Compress too much data
    final int payloadSize = serializedState.size();
    final Bytes payloadWithExtraData =
        Bytes.concatenate(serializedState, Bytes.fromHexString("0x01"));
    final Bytes compressed = compressor.compress(payloadWithExtraData);

    final InputStream input = new ByteArrayInputStream(compressed.toArrayUnsafe());
    assertThatThrownBy(() -> compressor.uncompress(input, payloadSize))
        .isInstanceOf(PayloadLargerThanExpectedException.class);
  }

  @Test
  public void uncompress_maliciousBytes() {
    // The number of underlying uncompressed bytes encoded
    final int uncompressedByteCount = 4;

    // Build a set of compressed data with a snappy header, and one frame for each uncompressed byte
    final Bytes singleByte = compressor.compress(Bytes.of(0x01));
    final Bytes singleByteFrame = singleByte.slice(SNAPPY_HEADER.size());
    final Bytes[] headerAndFrames = new Bytes[uncompressedByteCount + 1];
    headerAndFrames[0] = SNAPPY_HEADER;
    for (int i = 0; i < uncompressedByteCount; i++) {
      headerAndFrames[i + 1] = singleByteFrame;
    }
    final Bytes maliciousPayload = Bytes.concatenate(headerAndFrames);

    // Check assumptions - we want to build a set of bytes with valid frames that
    // exceeds the maximum expected compressed size given the underlying data
    final int maxExpectedCompressedBytes = compressor.getMaxCompressedLength(uncompressedByteCount);
    assertThat(maliciousPayload.size()).isGreaterThan(maxExpectedCompressedBytes);

    final InputStream input = new ByteArrayInputStream(maliciousPayload.toArray());
    assertThatThrownBy(() -> compressor.uncompress(input, uncompressedByteCount))
        .isInstanceOf(CompressionException.class);
  }

  @Test
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
      assertThatThrownBy(() -> compressor.uncompress(inputStream, bytesToRead))
          .isInstanceOf(CompressionException.class);
    }
  }
}
