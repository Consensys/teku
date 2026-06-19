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

package tech.pegasys.teku.benchmarks;

import static org.xerial.snappy.Snappy.compress;
import static org.xerial.snappy.Snappy.maxCompressedLength;
import static org.xerial.snappy.Snappy.uncompress;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.Snappy;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks the raw Snappy block format implementations available in Teku.
 *
 * <p>Decode benchmarks use the same snappy-java encoded payload to isolate decoder cost from
 * encoder-specific output shape.
 *
 * <p>Run with:
 *
 * <pre>./gradlew :eth-benchmark-tests:jmh --args="SnappyCodecBenchmark"</pre>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SnappyCodecBenchmark {

  private static final int RANDOM_SEED = 5566;
  private static final int REPEATING_PATTERN_SIZE = 256;

  @Param({"1024", "65536", "1048576"})
  public int payloadSize;

  @Param({"RANDOM", "REPEATING", "MIXED"})
  public PayloadPattern payloadPattern;

  private byte[] payload;
  private byte[] snappyJavaCompressedPayload;
  private Snappy nettySnappy;

  @Setup
  public void setup() throws IOException {
    payload = createPayload(payloadSize, payloadPattern);
    snappyJavaCompressedPayload = compress(payload);
    nettySnappy = new Snappy();

    assertRoundTrip("snappy-java", uncompress(snappyJavaCompressedPayload));
    assertRoundTrip("netty", nettyDecode(snappyJavaCompressedPayload));
    assertRoundTrip("netty encoded", uncompress(nettyEncode(payload)));
  }

  @Benchmark
  public byte[] snappyJavaEncode() throws IOException {
    return compress(payload);
  }

  @Benchmark
  public byte[] nettyEncode() {
    return nettyEncode(payload);
  }

  @Benchmark
  public byte[] snappyJavaDecode() throws IOException {
    return uncompress(snappyJavaCompressedPayload);
  }

  @Benchmark
  public byte[] nettyDecode() {
    return nettyDecode(snappyJavaCompressedPayload);
  }

  private byte[] nettyEncode(final byte[] uncompressed) {
    final ByteBuf in = Unpooled.wrappedBuffer(uncompressed);
    final ByteBuf out = Unpooled.buffer(maxCompressedLength(uncompressed.length));
    try {
      nettySnappy.encode(in, out, uncompressed.length);
      return readBytes(out);
    } finally {
      in.release();
      out.release();
    }
  }

  private byte[] nettyDecode(final byte[] compressed) {
    final ByteBuf in = Unpooled.wrappedBuffer(compressed);
    final ByteBuf out = Unpooled.buffer(payloadSize, payloadSize);
    try {
      nettySnappy.decode(in, out);
      return readBytes(out);
    } finally {
      nettySnappy.reset();
      in.release();
      out.release();
    }
  }

  private static byte[] readBytes(final ByteBuf byteBuf) {
    final byte[] bytes = new byte[byteBuf.readableBytes()];
    byteBuf.readBytes(bytes);
    return bytes;
  }

  private void assertRoundTrip(final String implementation, final byte[] decoded) {
    if (!Arrays.equals(payload, decoded)) {
      throw new IllegalStateException(implementation + " failed Snappy round-trip");
    }
  }

  private static byte[] createPayload(final int size, final PayloadPattern pattern) {
    final byte[] payload = new byte[size];
    final Random random = new Random(RANDOM_SEED);
    switch (pattern) {
      case RANDOM -> random.nextBytes(payload);
      case REPEATING -> {
        fillRepeatingPayload(payload, 0, payload.length, random);
      }
      case MIXED -> {
        final int randomByteCount = size / 2;
        random.nextBytes(payload);
        fillRepeatingPayload(payload, randomByteCount, size - randomByteCount, random);
      }
    }
    return payload;
  }

  private static void fillRepeatingPayload(
      final byte[] payload, final int startIndex, final int length, final Random random) {
    final byte[] repeatedPattern = new byte[REPEATING_PATTERN_SIZE];
    random.nextBytes(repeatedPattern);
    for (int i = 0; i < length; i++) {
      payload[startIndex + i] = repeatedPattern[i % repeatedPattern.length];
    }
  }

  public enum PayloadPattern {
    RANDOM,
    REPEATING,
    MIXED
  }
}
