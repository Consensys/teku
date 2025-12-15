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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.benchmarks.gen.LegacyLRUCache;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.CaffeineCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;

@Threads(Threads.MAX)
@Fork(2)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
public class TransitionCachesBenchmark {

  private static final IntList SOME_INT_LIST = IntList.of(1, 2, 3, 4, 5);
  private static final TekuPair<UInt64, UInt64> CONTENDED_MISS_KEY =
      TekuPair.of(UInt64.MAX_VALUE, UInt64.MAX_VALUE);

  public enum CacheType {
    CAFFEINE(CaffeineCache::create),
    LEGACY_LRU(LegacyLRUCache::create);

    private final TransitionCaches.CacheFactory cacheFactory;

    CacheType(final TransitionCaches.CacheFactory cacheFactory) {
      this.cacheFactory = cacheFactory;
    }

    public TransitionCaches createCaches() {
      return new TransitionCaches(cacheFactory);
    }
  }

  @State(Scope.Benchmark)
  public static class BenchmarkState {

    @Param({"CAFFEINE", "LEGACY_LRU"})
    private CacheType cacheType;

    @Param({"0", "5"})
    public long fallbackDelayMs;

    private static final int KEY_SPACE = 4096;

    private TekuPair<UInt64, UInt64>[] hitKeys;
    private TekuPair<UInt64, UInt64>[] missKeys;

    TransitionCaches caches;

    @Setup(Level.Trial)
    public void init() {
      caches = cacheType.createCaches();
      final Cache<TekuPair<UInt64, UInt64>, IntList> committeeCache = caches.getBeaconCommittee();

      // hit keys
      hitKeys = new TekuPair[KEY_SPACE];
      for (int i = 0; i < KEY_SPACE; i++) {
        hitKeys[i] = TekuPair.of(UInt64.valueOf(i), UInt64.ZERO);
        committeeCache.invalidateWithNewValue(hitKeys[i], SOME_INT_LIST);
      }

      // miss keys
      missKeys = new TekuPair[KEY_SPACE];
      for (int i = 0; i < KEY_SPACE; i++) {
        missKeys[i] = TekuPair.of(UInt64.valueOf(KEY_SPACE * 2 + i), UInt64.ZERO);
      }
    }

    private IntList slowFallback(final TekuPair<UInt64, UInt64> key) {
      if (fallbackDelayMs > 0) {
        try {
          TimeUnit.MILLISECONDS.sleep(fallbackDelayMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return SOME_INT_LIST;
    }
  }

  /**
   * Simulates a realistic workload with a high cache hit rate (90% hits, 10% misses). This
   * benchmark measures general throughput under normal conditions. When `fallbackDelayMs` is 0, it
   * shows raw performance. When > 0, it shows how occasional slow operations affect overall
   * throughput.
   */
  @Benchmark
  public void realisticWorkload(final BenchmarkState state, final Blackhole bh) {
    final Cache<TekuPair<UInt64, UInt64>, IntList> cache = state.caches.getBeaconCommittee();
    final TekuPair<UInt64, UInt64> key;

    // 90% chance of a cache hit
    if (ThreadLocalRandom.current().nextInt(10) < 9) {
      key = state.hitKeys[ThreadLocalRandom.current().nextInt(BenchmarkState.KEY_SPACE)];
    } else {
      key = state.missKeys[ThreadLocalRandom.current().nextInt(BenchmarkState.KEY_SPACE)];
    }

    final IntList result = cache.get(key, state::slowFallback);
    bh.consume(result);
  }

  /**
   * A targeted stress test where ALL threads request the SAME missing key concurrently. This is
   * designed to expose the contention caused by the `synchronized` block in `LegacyLRUCache` when a
   * slow fallback is executed.
   */
  @Benchmark
  public void contendedMissWithSlowFallback(final BenchmarkState state, final Blackhole bh) {
    // all threads contend on the exact same missing key.
    final Cache<TekuPair<UInt64, UInt64>, IntList> cache = state.caches.getBeaconCommittee();
    final IntList result = cache.get(CONTENDED_MISS_KEY, state::slowFallback);
    bh.consume(result);
  }

  /** Measures the performance of creating a copy of the caches */
  @Benchmark
  public void copyCaches(final BenchmarkState state, final Blackhole bh) {
    final TransitionCaches copy = state.caches.copy();
    bh.consume(copy);
  }
}
