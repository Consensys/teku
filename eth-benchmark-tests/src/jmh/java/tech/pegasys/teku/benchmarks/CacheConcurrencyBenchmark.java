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
    for (int i = 0; i < cacheSize; i++) {
      cache.get(i, key -> "Value:" + key);
    }
  }

  // real-world 5:1 workload
  // 6 threads for get() and 2 for invalidate()
  @Benchmark
  @Group("realWorldWorkload")
  @GroupThreads(6)
  public void getOperation(final Blackhole bh) {
    // 5 operations per call
    for (int i = 0; i < 5; i++) {
      int key = ThreadLocalRandom.current().nextInt(keySpace);
      String value = cache.get(key, k -> "Value:" + k);
      bh.consume(value);
    }
  }

  @Benchmark
  @Group("realWorldWorkload")
  @GroupThreads(2)
  public void invalidateOperation() {
    // 1 operation per call
    int key = ThreadLocalRandom.current().nextInt(keySpace);
    cache.invalidateWithNewValue(key, "NewValue:" + key);
  }
}
