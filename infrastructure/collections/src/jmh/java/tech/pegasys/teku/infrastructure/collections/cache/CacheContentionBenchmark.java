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

package tech.pegasys.teku.infrastructure.collections.cache;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class CacheContentionBenchmark {

  private static final int HIT_KEY_SPACE = 4_096;
  private static final int GROWTH_KEY_SPACE = 16_384;

  @Param({"LRU", "CONCURRENT_MAP", "ARRAY_INDEXED"})
  private String implementation;

  private Cache<Integer, Integer> hitCache;
  private Cache<Integer, Integer> growthCache;
  private AtomicInteger hitCursor;
  private AtomicInteger growthCursor;

  @Setup(Level.Trial)
  public void setUp() {
    hitCache = createCache();
    growthCache = createCache();
    hitCursor = new AtomicInteger();
    growthCursor = new AtomicInteger(HIT_KEY_SPACE);

    for (int i = 0; i < HIT_KEY_SPACE; i++) {
      hitCache.invalidateWithNewValue(i, i);
      growthCache.invalidateWithNewValue(i, i);
    }
  }

  @Benchmark
  @Fork(1)
  @Threads(8)
  public void readMostlyHits(final Blackhole blackhole) {
    final int key = Math.floorMod(hitCursor.getAndIncrement(), HIT_KEY_SPACE);
    blackhole.consume(hitCache.get(key, idx -> idx));
  }

  @Benchmark
  @Fork(1)
  @Threads(8)
  public void mixedHitsAndGrowth(final Blackhole blackhole) {
    final int sequence = growthCursor.getAndIncrement();
    final int key =
        Math.floorMod(sequence, 8) == 0
            ? HIT_KEY_SPACE + Math.floorMod(sequence, GROWTH_KEY_SPACE - HIT_KEY_SPACE)
            : Math.floorMod(sequence, HIT_KEY_SPACE);
    blackhole.consume(growthCache.get(key, idx -> idx));
  }

  private Cache<Integer, Integer> createCache() {
    return switch (implementation) {
      case "LRU" -> LRUCache.create(GROWTH_KEY_SPACE * 2);
      case "CONCURRENT_MAP" -> new ConcurrentMapCache<>();
      case "ARRAY_INDEXED" -> new ArrayIndexedCache<>(Integer::intValue);
      default -> throw new IllegalStateException("Unexpected cache implementation: " + implementation);
    };
  }
}
