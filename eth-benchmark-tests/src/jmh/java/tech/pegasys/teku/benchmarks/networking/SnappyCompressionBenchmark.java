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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.Snappy;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Compares snappy-java (native, used today for gossip block compression) against Netty's pure-Java
 * Snappy block codec (used today for RPC framing), to evaluate consolidating on a single Snappy
 * implementation. Both implementations are invoked via their raw library APIs so the comparison
 * isolates the compression engine; the {@code byte[]} marshalling Netty requires is included
 * because gossip code speaks byte arrays.
 *
 * <p>Run a specific payload/impl with GC profiling:
 *
 * <pre>
 *   ./gradlew :eth-benchmark-tests:jmh --args="SnappyCompressionBenchmark -prof gc"
 * </pre>
 */
@Fork(1)
@State(Scope.Thread)
public class SnappyCompressionBenchmark {

  public enum Impl {
    SNAPPY_JAVA,
    NETTY
  }

  public enum Payload {
    ATTESTATION,
    AGGREGATE,
    SYNC_COMMITTEE_MESSAGE,
    BEACON_BLOCK,
    DATA_COLUMN_SIDECAR
  }

  @State(Scope.Benchmark)
  public static class Data {

    @Param({
      "ATTESTATION",
      "AGGREGATE",
      "SYNC_COMMITTEE_MESSAGE",
      "BEACON_BLOCK",
      "DATA_COLUMN_SIDECAR"
    })
    public Payload payload;

    @Param({"SNAPPY_JAVA", "NETTY"})
    public Impl impl;

    // Uncompressed SSZ bytes (input for the compress benchmark).
    public byte[] raw;
    // Pre-compressed bytes produced by the impl under test (input for the decompress benchmark).
    public byte[] compressed;

    @Setup(Level.Trial)
    public void setup() throws IOException {
      final Spec spec = TestSpecFactory.createMainnetFulu();
      final DataStructureUtil data = new DataStructureUtil(1, spec);
      final Bytes ssz =
          switch (payload) {
            case ATTESTATION -> data.randomAttestation().sszSerialize();
            case AGGREGATE -> data.randomSignedAggregateAndProof().sszSerialize();
            case SYNC_COMMITTEE_MESSAGE -> data.randomSyncCommitteeMessage().sszSerialize();
            case BEACON_BLOCK -> data.randomSignedBeaconBlock().sszSerialize();
            case DATA_COLUMN_SIDECAR -> data.randomDataColumnSidecar().sszSerialize();
          };
      raw = ssz.toArrayUnsafe();

      compressed =
          switch (impl) {
            case SNAPPY_JAVA -> snappyJavaCompress(raw);
            case NETTY -> nettyCompress(raw);
          };

      // Feasibility check (spec item 1): the two implementations must produce/consume the same
      // Snappy block wire format in both directions, or a migration would break gossip
      // interoperability. Fail fast and loudly if not.
      verifyCrossCompatibility(raw);
    }
  }

  @Benchmark
  @Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public byte[] compress(final Data data) throws IOException {
    return switch (data.impl) {
      case SNAPPY_JAVA -> snappyJavaCompress(data.raw);
      case NETTY -> nettyCompress(data.raw);
    };
  }

  @Benchmark
  @Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public byte[] decompress(final Data data) throws IOException {
    return switch (data.impl) {
      case SNAPPY_JAVA -> snappyJavaUncompress(data.compressed);
      case NETTY -> nettyUncompress(data.compressed);
    };
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

  static void verifyCrossCompatibility(final byte[] raw) throws IOException {
    final byte[] viaNetty = nettyCompress(raw);
    if (!Arrays.equals(raw, snappyJavaUncompress(viaNetty))) {
      throw new IllegalStateException(
          "snappy-java failed to round-trip Netty-compressed output (wire format mismatch)");
    }
    final byte[] viaJava = snappyJavaCompress(raw);
    if (!Arrays.equals(raw, nettyUncompress(viaJava))) {
      throw new IllegalStateException(
          "Netty failed to round-trip snappy-java-compressed output (wire format mismatch)");
    }
  }
}
