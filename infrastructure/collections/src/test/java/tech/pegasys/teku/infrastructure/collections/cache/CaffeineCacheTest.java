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
  private final Cache<Integer, Integer> cache = CacheTestUtil.createSynchronous(maxCacheSize);

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
    final Cache<Integer, Integer> smallCache = CacheTestUtil.createSynchronous(maxCacheSize);
    for (int i = 0; i < maxCacheSize; i++) {
      smallCache.get(i, key -> key);
    }
    assertThat(smallCache.size()).isEqualTo(maxCacheSize);
    smallCache.get(maxCacheSize, key -> key);
    assertThat(smallCache.size()).isEqualTo(maxCacheSize);
    assertThat(smallCache.getCached(maxCacheSize)).isPresent();
  }

  @Test
  void get_shouldEvictAnItemWhenCapacityIsReached() {
    // fill the cache to its maximum capacity
    for (int i = 0; i < maxCacheSize; i++) {
      cache.get(i, key -> key);
    }
    assertThat(cache.size()).isEqualTo(maxCacheSize);

    // add a new item, which should trigger an eviction
    int newItemKey = maxCacheSize;
    cache.get(newItemKey, key -> key);

    // verify the cache's contract
    assertThat(cache.size())
        .as("Cache should not grow beyond its max size")
        .isEqualTo(maxCacheSize);
    assertThat(cache.getCached(newItemKey))
        .as("The newly added item should be present")
        .isPresent();

    long originalItemsStillInCache = 0;
    for (int i = 0; i < maxCacheSize; i++) {
      if (cache.getCached(i).isPresent()) {
        originalItemsStillInCache++;
      }
    }
    assertThat(originalItemsStillInCache)
        .as("Exactly one of the original items should have been evicted")
        .isEqualTo(maxCacheSize - 1);
  }

  @Test
  void copy_shouldPreserveCapacityLimit() {
    final Cache<Integer, Integer> sourceCache = CacheTestUtil.createSynchronous(maxCacheSize);
    for (int i = 0; i < maxCacheSize; i++) {
      sourceCache.get(i, key -> key);
    }
    assertThat(sourceCache.size()).isEqualTo(maxCacheSize);

    // create a copy
    final Cache<Integer, Integer> copiedCache = sourceCache.copy();
    assertThat(copiedCache.size()).isEqualTo(maxCacheSize);

    int newItemKey = maxCacheSize;
    copiedCache.get(newItemKey, key -> key);

    // the copied cache respected the size limit and did not grow
    assertThat(copiedCache.size())
        .as("Copied cache should evict old entries and not exceed its capacity")
        .isEqualTo(maxCacheSize);
    assertThat(copiedCache.getCached(newItemKey)).isPresent();

    // verify the original cache was not affected
    assertThat(sourceCache.size()).isEqualTo(maxCacheSize);
    assertThat(sourceCache.getCached(0)).isPresent();
    assertThat(sourceCache.getCached(newItemKey)).isEmpty();
  }
}
