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

package tech.pegasys.teku.benchmarks.networking;

import io.airlift.compress.v3.Compressor;
import io.airlift.compress.v3.Decompressor;
import io.airlift.compress.v3.snappy.SnappyJavaCompressor;
import io.airlift.compress.v3.snappy.SnappyJavaDecompressor;
import io.airlift.compress.v3.snappy.SnappyNativeCompressor;
import io.airlift.compress.v3.snappy.SnappyNativeDecompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.Snappy;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Compares Snappy block-compression implementations to evaluate consolidating on a single Snappy
 * dependency for gossip:
 *
 * <ul>
 *   <li>{@code SNAPPY_JAVA} — snappy-java, JNI to native C++ (used today for gossip).
 *   <li>{@code NETTY} — Netty's pure-Java Snappy block codec (used today for RPC framing).
 *   <li>{@code AIRCOMPRESSOR_NATIVE} — aircompressor-v3 native Snappy via {@code java.lang.foreign}
 *       (FFM, not JNI).
 *   <li>{@code AIRCOMPRESSOR_JAVA} — aircompressor-v3 pure-Java Snappy.
 * </ul>
 *
 * <p>Each implementation is invoked via its raw library API so the comparison isolates the
 * compression engine; {@code byte[]} marshalling (which Netty and aircompressor require) is
 * included because gossip code speaks byte arrays.
 *
 * <p>The {@code AIRCOMPRESSOR_NATIVE} classes are direct FFM bindings with no silent pure-Java
 * fallback, so a successful run (which passes the startup cross-compatibility round-trip) proves
 * the native library actually loaded and executed on this platform.
 *
 * <p>Compression ratio matters: Snappy fixes the wire format but not the encoder heuristics, so two
 * implementations can produce different compressed sizes for the same input — a faster encoder that
 * compresses less is not actually better for gossip bandwidth. Each implementation's compressed
 * size and ratio are therefore reported (deterministically, in {@code @Setup}, as {@code RATIO|...}
 * lines) alongside the timing metrics. To keep the comparison fair, {@code decompress} decodes a
 * single shared snappy-java-encoded buffer for every implementation (all are wire-compatible), so
 * decode cost is isolated from encoder-specific output shape. Synthetic {@code SYNTHETIC_*}
 * payloads exercise the encoder heuristics on compressible data, which the high-entropy SSZ message
 * payloads cannot.
 *
 * <p>Run a specific payload/impl with GC profiling:
 *
 * <pre>
 *   ./gradlew :eth-benchmark-tests:jmh --args="SnappyCompressionBenchmark -prof gc"
 * </pre>
 */
@Fork(1)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class SnappyCompressionBenchmark {

  public enum Impl {
    SNAPPY_JAVA,
    NETTY,
    AIRCOMPRESSOR_NATIVE,
    AIRCOMPRESSOR_JAVA
  }

  public enum Payload {
    ATTESTATION,
    AGGREGATE,
    SYNC_COMMITTEE_MESSAGE,
    BEACON_BLOCK,
    DATA_COLUMN_SIDECAR,
    // Synthetic payloads to exercise encoder heuristics on compressible data (the SSZ message
    // payloads above are high-entropy and barely compress).
    SYNTHETIC_REPEATING_64K,
    SYNTHETIC_MIXED_64K
  }

  @State(Scope.Benchmark)
  public static class Data {

    @Param({
      "ATTESTATION",
      "AGGREGATE",
      "SYNC_COMMITTEE_MESSAGE",
      "BEACON_BLOCK",
      "DATA_COLUMN_SIDECAR",
      "SYNTHETIC_REPEATING_64K",
      "SYNTHETIC_MIXED_64K"
    })
    public Payload payload;

    @Param({"SNAPPY_JAVA", "NETTY", "AIRCOMPRESSOR_NATIVE", "AIRCOMPRESSOR_JAVA"})
    public Impl impl;

    // Uncompressed payload (input for the compress benchmark).
    public byte[] raw;
    // Shared reference compressed bytes (snappy-java block format), used as the decompress input
    // for
    // EVERY implementation so decode is compared on byte-identical input (all impls are
    // wire-compatible). Isolates decoder cost from encoder-specific output shape.
    public byte[] referenceCompressed;

    @Setup(Level.Trial)
    public void setup() throws IOException {
      final Spec spec = TestSpecFactory.createMainnetFulu();
      final DataStructureUtil data = new DataStructureUtil(1, spec);
      raw = buildPayload(payload, data).toArrayUnsafe();
      referenceCompressed = snappyJavaCompress(raw);

      // Report compressed size / ratio for the implementation under test. Deterministic, so it is
      // computed once here and logged rather than via JMH timing counters (avoids the AuxCounters
      // pitfalls that misreport floating-point ratios).
      final byte[] ownCompressed =
          switch (impl) {
            case SNAPPY_JAVA -> snappyJavaCompress(raw);
            case NETTY -> nettyCompress(raw);
            case AIRCOMPRESSOR_NATIVE -> aircompressorCompress(new SnappyNativeCompressor(), raw);
            case AIRCOMPRESSOR_JAVA -> aircompressorCompress(new SnappyJavaCompressor(), raw);
          };
      System.out.printf(
          "RATIO|%s|%s|raw=%d|compressed=%d|ratio=%.4f%n",
          payload,
          impl,
          raw.length,
          ownCompressed.length,
          (double) raw.length / ownCompressed.length);

      // Feasibility check (spec item 1): every implementation must produce/consume the same Snappy
      // block wire format as snappy-java, or a migration would break gossip interoperability. Fail
      // fast and loudly if not. Impl-agnostic, so run once per payload (during the SNAPPY_JAVA
      // trial).
      if (impl == Impl.SNAPPY_JAVA) {
        verifyCrossCompatibility(raw);
      }
    }
  }

  @Benchmark
  @Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public byte[] compress(final Data data) throws IOException {
    return switch (data.impl) {
      case SNAPPY_JAVA -> snappyJavaCompress(data.raw);
      case NETTY -> nettyCompress(data.raw);
      case AIRCOMPRESSOR_NATIVE -> aircompressorCompress(new SnappyNativeCompressor(), data.raw);
      case AIRCOMPRESSOR_JAVA -> aircompressorCompress(new SnappyJavaCompressor(), data.raw);
    };
  }

  @Benchmark
  @Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public byte[] decompress(final Data data) throws IOException {
    return switch (data.impl) {
      case SNAPPY_JAVA -> snappyJavaUncompress(data.referenceCompressed);
      case NETTY -> nettyUncompress(data.referenceCompressed);
      case AIRCOMPRESSOR_NATIVE ->
          aircompressorUncompress(
              new SnappyNativeDecompressor(), data.referenceCompressed, data.raw.length);
      case AIRCOMPRESSOR_JAVA ->
          aircompressorUncompress(
              new SnappyJavaDecompressor(), data.referenceCompressed, data.raw.length);
    };
  }

  private static final int SYNTHETIC_PAYLOAD_SIZE = 65536;
  private static final int REPEATING_PATTERN_SIZE = 256;
  private static final long SYNTHETIC_SEED = 5566;

  private static Bytes buildPayload(final Payload payload, final DataStructureUtil data) {
    return switch (payload) {
      case ATTESTATION -> data.randomAttestation().sszSerialize();
      case AGGREGATE -> data.randomSignedAggregateAndProof().sszSerialize();
      case SYNC_COMMITTEE_MESSAGE -> data.randomSyncCommitteeMessage().sszSerialize();
      case BEACON_BLOCK -> data.randomSignedBeaconBlock().sszSerialize();
      case DATA_COLUMN_SIDECAR -> data.randomDataColumnSidecar().sszSerialize();
      case SYNTHETIC_REPEATING_64K -> Bytes.wrap(syntheticRepeating(SYNTHETIC_PAYLOAD_SIZE));
      case SYNTHETIC_MIXED_64K -> Bytes.wrap(syntheticMixed(SYNTHETIC_PAYLOAD_SIZE));
    };
  }

  private static byte[] syntheticRepeating(final int size) {
    final byte[] out = new byte[size];
    final Random random = new Random(SYNTHETIC_SEED);
    final byte[] pattern = new byte[REPEATING_PATTERN_SIZE];
    random.nextBytes(pattern);
    for (int i = 0; i < size; i++) {
      out[i] = pattern[i % pattern.length];
    }
    return out;
  }

  private static byte[] syntheticMixed(final int size) {
    final byte[] out = new byte[size];
    final Random random = new Random(SYNTHETIC_SEED);
    random.nextBytes(out);
    final int half = size / 2;
    final byte[] pattern = new byte[REPEATING_PATTERN_SIZE];
    random.nextBytes(pattern);
    for (int i = half; i < size; i++) {
      out[i] = pattern[(i - half) % pattern.length];
    }
    return out;
  }

  static byte[] snappyJavaCompress(final byte[] in) throws IOException {
    return org.xerial.snappy.Snappy.compress(in);
  }

  static byte[] snappyJavaUncompress(final byte[] in) throws IOException {
    return org.xerial.snappy.Snappy.uncompress(in);
  }

  static byte[] nettyCompress(final byte[] in) {
    final ByteBuf inBuf = Unpooled.wrappedBuffer(in);
    final ByteBuf outBuf = Unpooled.buffer(32 + in.length / 2);
    try {
      new Snappy().encode(inBuf, outBuf, inBuf.readableBytes());
      final byte[] out = new byte[outBuf.readableBytes()];
      outBuf.readBytes(out);
      return out;
    } finally {
      inBuf.release();
      outBuf.release();
    }
  }

  static byte[] nettyUncompress(final byte[] in) {
    final ByteBuf inBuf = Unpooled.wrappedBuffer(in);
    final ByteBuf outBuf = Unpooled.buffer(in.length * 4);
    try {
      new Snappy().decode(inBuf, outBuf);
      final byte[] out = new byte[outBuf.readableBytes()];
      outBuf.readBytes(out);
      return out;
    } finally {
      inBuf.release();
      outBuf.release();
    }
  }

  static byte[] aircompressorCompress(final Compressor compressor, final byte[] in) {
    final byte[] out = new byte[compressor.maxCompressedLength(in.length)];
    final int written = compressor.compress(in, 0, in.length, out, 0, out.length);
    return Arrays.copyOf(out, written);
  }

  static byte[] aircompressorUncompress(
      final Decompressor decompressor, final byte[] in, final int uncompressedLength) {
    final byte[] out = new byte[uncompressedLength];
    final int written = decompressor.decompress(in, 0, in.length, out, 0, out.length);
    return written == out.length ? out : Arrays.copyOf(out, written);
  }

  /**
   * Verifies every implementation shares the Snappy block wire format with snappy-java (the current
   * production gossip codec), in both directions. A mismatch means a migration would break gossip
   * interoperability with the network. Exercising the aircompressor-native path here also proves
   * its FFM native library loaded on this platform (there is no silent pure-Java fallback in the
   * {@code *Native*} classes).
   */
  static void verifyCrossCompatibility(final byte[] raw) throws IOException {
    final byte[] snappyJava = snappyJavaCompress(raw);

    // snappy-java must decode every implementation's compressed output.
    requireRoundTrip("snappy-java <- Netty", raw, snappyJavaUncompress(nettyCompress(raw)));
    requireRoundTrip(
        "snappy-java <- aircompressor-native",
        raw,
        snappyJavaUncompress(aircompressorCompress(new SnappyNativeCompressor(), raw)));
    requireRoundTrip(
        "snappy-java <- aircompressor-java",
        raw,
        snappyJavaUncompress(aircompressorCompress(new SnappyJavaCompressor(), raw)));

    // Every implementation must decode snappy-java's compressed output.
    requireRoundTrip("Netty <- snappy-java", raw, nettyUncompress(snappyJava));
    requireRoundTrip(
        "aircompressor-native <- snappy-java",
        raw,
        aircompressorUncompress(new SnappyNativeDecompressor(), snappyJava, raw.length));
    requireRoundTrip(
        "aircompressor-java <- snappy-java",
        raw,
        aircompressorUncompress(new SnappyJavaDecompressor(), snappyJava, raw.length));
  }

  private static void requireRoundTrip(
      final String description, final byte[] expected, final byte[] actual) {
    if (!Arrays.equals(expected, actual)) {
      throw new IllegalStateException(
          "Snappy block wire-format mismatch ("
              + description
              + "): cross-decode did not reproduce the original input");
    }
  }
}
