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

package tech.pegasys.teku.ssz.SSZTypes;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
public class BitlistBenchmark {
  private static final int BITLIST_SIZE = 128; // MainNet target committee size
  private static final Bitlist LAST_BIT_SET = createBitlist(127);

  private static final Bitlist MANY_BITS_SET =
      createBitlist(
          1, 2, 6, 16, 23, 33, 65, 87, 96, 100, 101, 102, 103, 104, 110, 115, 120, 121, 125);

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void intersects(Blackhole bh) {
    bh.consume(LAST_BIT_SET.intersects(LAST_BIT_SET));
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void setAllBits(Blackhole bh) {
    final Bitlist target = createBitlist();
    target.setAllBits(MANY_BITS_SET);
    bh.consume(target);
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void getAttestingIndicies(Blackhole bh) {
    bh.consume(MANY_BITS_SET.getAllSetBits());
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void countSetBits(Blackhole bh) {
    bh.consume(MANY_BITS_SET.getBitCount());
  }

  private static Bitlist createBitlist(final int... setBits) {
    final Bitlist bitlist = new Bitlist(BITLIST_SIZE, BITLIST_SIZE);
    IntStream.of(setBits).forEach(bitlist::setBit);
    return bitlist;
  }
}
