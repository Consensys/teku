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
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.StripedCache;

/** Benchmark comparing Striped LRU Cache to a single synchronized LRU cache implementation */
@State(Scope.Group)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(
    value = 3,
    jvmArgs = {"-Xms2G", "-Xmx2G"})
public class CacheConcurrencyBenchmark {

  @Param({"LRU", "STRIPED"})
  public String cacheType;

  @Param({"1024"})
  public int cacheSize;

  @Param({"2048"})
  public int keySpace;

  @Param({"5"})
  public long fallbackDelayMs;

  private Cache<Integer, String> cache;

  @Setup(Level.Trial)
  public void setup() {
    switch (cacheType) {
      case "LRU":
        cache = LRUCache.create(cacheSize);
        break;
      case "STRIPED":
        cache = StripedCache.createUnbounded();
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
    // only access keys that are guaranteed to be in cache
    final int key = ThreadLocalRandom.current().nextInt((int) (cacheSize * 0.8));
    final String value =
        cache.get(
            key,
            k -> {
              throw new IllegalStateException("Should not be called - key must be cached");
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
    // keys that should be in cache (but may be evicted due to concurrency)
    final int key = ThreadLocalRandom.current().nextInt((int) (cacheSize * 0.8));
    final String value =
        cache.get(
            key,
            k -> {
              // quick recovery value for keys that should be cached but might have been evicted due
              // to concurrent access
              return "CacheMiss:" + k;
            });
    bh.consume(value);
  }

  @Benchmark
  @Group("concurrentReadsWithSlowFallbacks")
  @GroupThreads(1)
  public void slowFallbacks(final Blackhole bh) {
    // access keys that always require fallback
    final int key = cacheSize + ThreadLocalRandom.current().nextInt(keySpace - cacheSize);
    final String value =
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
    // favors cache hits
    final int randomValue = ThreadLocalRandom.current().nextInt(100);
    final int key;

    // 90% cache hits (keys in first 80% of cache size)
    if (randomValue < 90) {
      key = ThreadLocalRandom.current().nextInt((int) (cacheSize * 0.8));
    } else {
      // 10% cache misses
      key = cacheSize + ThreadLocalRandom.current().nextInt(keySpace - cacheSize);
    }

    final String value =
        cache.get(
            key,
            k -> {
              // add a small delay (20% are slow)
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

  /**
   * Mixed Read and Write Workload. Simulates a workload with both reads and writes, testing
   * contention
   */
  @Benchmark
  @Group("mixedReadWriteScenario")
  @GroupThreads(8)
  public void mixedReadWriteOperations(final Blackhole bh) {
    // 80% reads, 20% writes
    if (ThreadLocalRandom.current().nextInt(100) < 80) {
      final int key = ThreadLocalRandom.current().nextInt(keySpace);
      final String value = cache.get(key, k -> "Value:" + k);
      bh.consume(value);
    } else {
      final int key = ThreadLocalRandom.current().nextInt(keySpace);
      cache.invalidateWithNewValue(key, "NewValue:" + key);
    }
  }
}
