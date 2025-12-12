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

public class CaffeineCacheTest {

  private final int maxCacheSize = 16;
  private final Cache<Integer, Integer> cache = CaffeineCache.create(maxCacheSize);

  @Test
  void concurrencyTest() {
    final Cache<Integer, Integer> cache = CaffeineCache.create(256);
    final int threadsCount = 16;
    final ExecutorService executor = Executors.newFixedThreadPool(threadsCount);

    final CompletableFuture<?>[] futures =
        Stream.generate(
                () ->
                    CompletableFuture.runAsync(
                        () -> {
                          Random random = new Random();
                          for (int iteration = 0; iteration < 100; iteration++) {
                            for (int i = 0; i < 256 * 16; i++) {
                              int key = random.nextInt(256 * 2);
                              Integer value = cache.get(key, idx -> idx);
                              assertThat(value).isEqualTo(key);
                            }
                            for (int i = 0; i < 256; i++) {
                              int key = random.nextInt(256 * 2);
                              cache.invalidate(key);
                            }
                            if (random.nextInt(threadsCount * 2) == 0) {
                              cache.clear();
                            }
                          }
                        },
                        executor))
            .limit(threadsCount)
            .toArray(CompletableFuture[]::new);

    try {
      CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      Assertions.fail("Concurrency test failed with exception", e);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void get_shouldCreateAnEntryWhenMiss() {
    Integer i = cache.get(1, __ -> 777);
    assertThat(i).isEqualTo(777);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void get_shouldReturnExistingEntryWhenHit() {
    cache.get(1, __ -> 777);
    Integer i = cache.get(1, __ -> 888);
    assertThat(i).isEqualTo(777);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void get_shouldEvictLeastRecentlyAccessedValue() {
    // fill the cache completely with keys 0 to 15
    for (int i = 0; i < maxCacheSize; i++) {
      cache.get(i, key -> 100 + key);
    }
    // at this point, the access order is 0, 1, 2, ..., 15
    // the least recently used item is 0

    // access key 0 again to make it the most recently used
    cache.get(0, __ -> 100);

    // now, the least recently used item is 1
    // add a new item to force an eviction
    cache.get(maxCacheSize, key -> 100 + key);
    assertThat(cache.size()).isEqualTo(maxCacheSize);

    // verify that the new least recently used item 1 is evicted
    assertThat(cache.getCached(1)).isEmpty();
    assertThat(cache.getCached(0)).contains(100);
    assertThat(cache.getCached(2)).contains(102);
  }

  @Test
  void invalidate_shouldRemoveEntry() {
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);
    cache.invalidate(0);

    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.getCached(0)).isEmpty();
    assertThat(cache.getCached(1)).contains(101);
  }

  @Test
  void invalidate_shouldNotModifyWithNonExistingKey() {
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);
    cache.invalidate(2);

    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.getCached(0)).contains(100);
    assertThat(cache.getCached(1)).contains(101);
  }

  @Test
  void get_shouldHonorMaxCapacityAfterEviction() {
    final Cache<Integer, Integer> cache = CaffeineCache.create(maxCacheSize);
    for (int i = 0; i < maxCacheSize; i++) {
      cache.get(i, key -> key);
    }
    assertThat(cache.size()).isEqualTo(maxCacheSize);
    cache.get(maxCacheSize, key -> key);
    assertThat(cache.size()).isEqualTo(maxCacheSize);
    assertThat(cache.getCached(maxCacheSize)).isPresent();
  }

  @Test
  void invalidate_shouldEvictLeastRecentlyAccessed() {
    for (int i = 0; i < maxCacheSize; i++) {
      cache.get(i, key -> key);
    }
    cache.get(0, key -> key);
    cache.get(1, key -> key);

    // should evict least recently used entry '2'
    cache.get(maxCacheSize, key -> key);
    assertThat(cache.size()).isEqualTo(maxCacheSize);
    assertThat(cache.getCached(0)).contains(0);
    assertThat(cache.getCached(1)).contains(1);
    assertThat(cache.getCached(2)).isEmpty();
    assertThat(cache.getCached(3)).contains(3);
  }

  @Test
  void copy_shouldPreserveCapacityLimit() {
    // create a completely filled-up cache
    final Cache<Integer, Integer> sourceCache = CaffeineCache.create(maxCacheSize);
    for (int i = 0; i < maxCacheSize; i++) {
      sourceCache.get(i, key -> key);
    }
    assertThat(sourceCache.size()).isEqualTo(maxCacheSize);

    // create a copy
    final Cache<Integer, Integer> copiedCache = sourceCache.copy();
    assertThat(copiedCache.size()).isEqualTo(maxCacheSize);

    // add a new item to the copy to trigger eviction
    copiedCache.get(maxCacheSize, key -> key);

    // the copied cache respected the size limit and did not grow
    assertThat(copiedCache.size())
        .as("Copied cache should evict old entries and not exceed its capacity")
        .isEqualTo(maxCacheSize);

    // verify eviction occurred in the copy
    assertThat(copiedCache.getCached(0)).isEmpty();
    assertThat(copiedCache.getCached(maxCacheSize)).isPresent();

    // verify the original cache was not affected
    assertThat(sourceCache.size()).isEqualTo(maxCacheSize);
    assertThat(sourceCache.getCached(0)).isPresent();
    assertThat(sourceCache.getCached(maxCacheSize)).isEmpty();
  }
}
