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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArrayIndexedCacheTest {

  private final ArrayIndexedCache<Integer, String> cache = new ArrayIndexedCache<>(Integer::intValue);

  @Test
  void concurrencyTest() {
    final Random random = new Random();
    final int threadsCount = 16;
    final int keySpace = 256;
    final ArrayIndexedCache<Integer, Integer> concurrentCache =
        new ArrayIndexedCache<>(Integer::intValue);
    final ExecutorService executor = Executors.newFixedThreadPool(threadsCount);

    final CompletableFuture<?>[] futures =
        Stream.generate(
                () ->
                    CompletableFuture.runAsync(
                        () -> {
                          while (!Thread.interrupted()) {
                            for (int i = 0; i < keySpace * 16; i++) {
                              final int key = random.nextInt(keySpace * 2);
                              final Integer value = concurrentCache.get(key, idx -> idx);
                              assertThat(value).isEqualTo(key);
                            }
                            for (int i = 0; i < keySpace; i++) {
                              final int key = random.nextInt(keySpace * 2);
                              concurrentCache.invalidate(key);
                            }

                            if (random.nextInt(threadsCount * 2) == 0) {
                              concurrentCache.clear();
                            }

                            final Cache<Integer, Integer> copy = concurrentCache.copy();
                            for (int i = 0; i < keySpace * 8; i++) {
                              final int key = random.nextInt(keySpace * 2);
                              final Integer value = copy.get(key, idx -> idx);
                              assertThat(value).isEqualTo(key);
                            }
                          }
                        },
                        executor))
            .limit(threadsCount)
            .toArray(CompletableFuture[]::new);

    final CompletableFuture<Object> any = CompletableFuture.anyOf(futures);

    assertThatThrownBy(() -> any.get(5, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
    executor.shutdownNow();
  }

  @Test
  void shouldCreateEntryOnMiss() {
    final String value = cache.get(3, key -> "value-" + key);

    assertThat(value).isEqualTo("value-3");
    assertThat(cache.getCached(3)).contains("value-3");
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void shouldSupportSparseKeysWithoutEviction() {
    cache.invalidateWithNewValue(1, "one");
    cache.invalidateWithNewValue(1_000, "thousand");

    assertThat(cache.getCached(1)).contains("one");
    assertThat(cache.getCached(1_000)).contains("thousand");
    assertThat(cache.size()).isEqualTo(2);
  }

  @Test
  void copyShouldBeIndependent() {
    cache.invalidateWithNewValue(2, "two");

    final Cache<Integer, String> copy = cache.copy();
    cache.invalidate(2);
    cache.invalidateWithNewValue(4, "four");

    assertThat(copy.getCached(2)).contains("two");
    assertThat(copy.getCached(4)).isEmpty();
    assertThat(cache.getCached(2)).isEmpty();
  }

  @Test
  void clearShouldRemoveAllEntries() {
    cache.invalidateWithNewValue(2, "two");
    cache.invalidateWithNewValue(4, "four");

    cache.clear();

    assertThat(cache.getCached(2)).isEmpty();
    assertThat(cache.getCached(4)).isEmpty();
    assertThat(cache.size()).isZero();
  }

  @Test
  void sizeShouldCountCachedEntriesInsteadOfCapacity() {
    cache.invalidateWithNewValue(5, "five");
    cache.invalidateWithNewValue(50, "fifty");
    cache.invalidate(50);

    assertThat(cache.size()).isEqualTo(1);
  }
}
