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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ArrayIndexedCacheTest {

  @Test
  void shouldCreateEntryOnMiss() {
    final Cache<Integer, Integer> cache = new ArrayIndexedCache<>(Integer::intValue);

    assertThat(cache.get(32, value -> value * 2)).isEqualTo(64);
    assertThat(cache.getCached(32)).contains(64);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void shouldSupportSparseKeysWithoutEviction() {
    final Cache<Integer, Integer> cache = new ArrayIndexedCache<>(Integer::intValue);

    cache.get(1, value -> value);
    cache.get(1_000_000, value -> value);

    assertThat(cache.getCached(1)).contains(1);
    assertThat(cache.getCached(1_000_000)).contains(1_000_000);
    assertThat(cache.size()).isEqualTo(2);
  }

  @Test
  void copyShouldBeIndependent() {
    final Cache<Integer, Integer> source = new ArrayIndexedCache<>(Integer::intValue);
    source.get(8, value -> value);

    final Cache<Integer, Integer> copy = source.copy();
    copy.invalidateWithNewValue(8, 99);

    assertThat(source.getCached(8)).contains(8);
    assertThat(copy.getCached(8)).contains(99);
  }

  @Test
  void clearShouldRemoveAllEntries() {
    final Cache<Integer, Integer> cache = new ArrayIndexedCache<>(Integer::intValue);
    cache.get(4, value -> value);
    cache.get(16, value -> value);

    cache.clear();

    assertThat(cache.size()).isZero();
    assertThat(cache.getCached(4)).isEmpty();
    assertThat(cache.getCached(16)).isEmpty();
  }

  @Test
  void concurrencyShouldRemainSafe() {
    final Cache<Integer, Integer> cache = new ArrayIndexedCache<>(Integer::intValue);
    final int threadsCount = 16;
    final ExecutorService executor = Executors.newFixedThreadPool(threadsCount);

    final CompletableFuture<?>[] futures =
        Stream.generate(
                () ->
                    CompletableFuture.runAsync(
                        () -> {
                          final Random random = new Random();
                          for (int iteration = 0; iteration < 50; iteration++) {
                            for (int i = 0; i < 512; i++) {
                              final int key = random.nextInt(4096);
                              assertThat(cache.get(key, value -> value)).isEqualTo(key);
                            }
                            for (int i = 0; i < 64; i++) {
                              cache.invalidate(random.nextInt(4096));
                            }
                            if (random.nextInt(32) == 0) {
                              cache.clear();
                            }
                          }
                        },
                        executor))
            .limit(threadsCount)
            .toArray(CompletableFuture[]::new);

    try {
      CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      Assertions.fail("Concurrency test failed with exception", e);
    } finally {
      executor.shutdownNow();
    }
  }
}
