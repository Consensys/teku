/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.benchmarks.gen.LegacyLRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.CaffeineCache;

/** Benchmark comparing Caffeine Cache to a legacy LRU cache implementation */
@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(
    value = 3,
    jvmArgs = {"-Xms2G", "-Xmx2G"})
public class CacheConcurrencyBenchmark {
  @Param({"LEGACY_LRU", "CAFFEINE"})
  public String cacheType;

  @Param({"1024"})
  public int cacheSize;

  @Param({"2048"})
  public int keySpace;

  @Param({"50"})
  public long fallbackDelayMs;

  private Cache<Integer, String> cache;

  @Setup(Level.Trial)
  public void setup() {
    switch (cacheType) {
      case "LEGACY_LRU":
        cache = LegacyLRUCache.create(cacheSize);
        break;
      case "CAFFEINE":
        cache = CaffeineCache.create(cacheSize);
        break;
      default:
        throw new IllegalStateException("Unknown cache type: " + cacheType);
    }

    // populate 80% of the cache
    for (int i = 0; i < cacheSize * 0.8; i++) {
      cache.get(i, key -> "Value:" + key);
    }
  }

  /**
   * Pure read performance test with only cached values. Demonstrates basic throughput for cache
   * hits
   */
  @Benchmark
  @Group("pureReadPerformance")
  @GroupThreads(8)
  public void cachedReads(final Blackhole bh) {
    // Only access keys that are guaranteed to be in cache
    int key = ThreadLocalRandom.current().nextInt((int) (cacheSize * 0.8));
    String value =
        cache.get(
            key,
            k -> {
              throw new IllegalStateException("Should not be called - key should be cached");
            });
    bh.consume(value);
  }

  /**
   * Test non-blocking reads. One thread performs slow fallback operations that would block all
   * others in a synchronized cache
   */
  @Benchmark
  @Group("concurrentReadsWithSlowFallbacks")
  @GroupThreads(7)
  public void concurrentReads(final Blackhole bh) {
    // access only keys that are guaranteed to be in cache
    int key = ThreadLocalRandom.current().nextInt((int) (cacheSize * 0.8));
    String value =
        cache.get(
            key,
            k -> {
              throw new IllegalStateException("Should not be called - key should be cached");
            });
    bh.consume(value);
  }

  @Benchmark
  @Group("concurrentReadsWithSlowFallbacks")
  @GroupThreads()
  public void slowFallbacks(final Blackhole bh) {
    // access keys that always require fallback
    int key = cacheSize + ThreadLocalRandom.current().nextInt(keySpace - cacheSize);
    String value =
        cache.get(
            key,
            k -> {
              // slow fallback
              try {
                Thread.sleep(fallbackDelayMs);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return "SlowValue:" + k;
            });
    bh.consume(value);
  }

  /**
   * Real world scenario with mixed operations. Most reads hit the cache but occasionally require
   * fallbacks
   */
  @Benchmark
  @Group("realWorldScenario")
  @GroupThreads(8)
  public void mixedReadOperations(final Blackhole bh) {
    int key = ThreadLocalRandom.current().nextInt(keySpace);

    // 90% of reads should hit the cache, 10% require fallback
    String value =
        cache.get(
            key,
            k -> {
              // If it's a miss, 20% of the time it's a slow fallback
              if (ThreadLocalRandom.current().nextInt(5) == 0) {
                try {
                  Thread.sleep(fallbackDelayMs);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
              return "Value:" + k;
            });

    bh.consume(value);
  }
}
