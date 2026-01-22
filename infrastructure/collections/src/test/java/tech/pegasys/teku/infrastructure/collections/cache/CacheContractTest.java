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

/**
 * Abstract test class that defines the contract all Cache implementations must satisfy. Concrete
 * test classes should extend this and provide a cache factory via createCache().
 */
public abstract class CacheContractTest {

  /**
   * Factory method that subclasses must implement to create a cache instance for testing.
   *
   * @param capacity The capacity for bounded caches
   * @return A cache instance to test
   */
  protected abstract <K, V> Cache<K, V> createCache(int capacity);

  /**
   * Factory method for unbounded caches. Subclasses can override if they support unbounded caches.
   *
   * @return An unbounded cache instance, or null if not supported
   */
  protected <K, V> Cache<K, V> createUnboundedCache() {
    return null; // Default: unbounded not supported
  }

  @Test
  void concurrencyTest() {
    final Cache<Integer, Integer> cache = createCache(256);
    final int threadsCount = 16;
    final ExecutorService executor = Executors.newFixedThreadPool(threadsCount);

    final CompletableFuture<?>[] futures =
        Stream.generate(
                () ->
                    CompletableFuture.runAsync(
                        () -> {
                          final Random random = new Random();
                          for (int iteration = 0; iteration < 50; iteration++) {
                            for (int i = 0; i < 256 * 8; i++) {
                              int key = random.nextInt(256 * 2);
                              final Integer value = cache.get(key, idx -> idx);
                              assertThat(value).isEqualTo(key);
                            }
                            for (int i = 0; i < 128; i++) {
                              final int key = random.nextInt(256 * 2);
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
      CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      Assertions.fail("Concurrency test failed with exception", e);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void get_shouldCreateAnEntryWhenMiss() {
    final Cache<Integer, Integer> cache = createCache(16);
    Integer i = cache.get(1, __ -> 777);
    assertThat(i).isEqualTo(777);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void get_shouldReturnExistingEntryWhenHit() {
    final Cache<Integer, Integer> cache = createCache(16);
    cache.get(1, __ -> 777);
    Integer i = cache.get(1, __ -> 888);
    assertThat(i).isEqualTo(777);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void invalidate_shouldRemoveEntry() {
    final Cache<Integer, Integer> cache = createCache(16);
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);
    cache.invalidate(0);
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.getCached(0)).isEmpty();
    assertThat(cache.getCached(1)).contains(101);
  }

  @Test
  void invalidate_shouldNotModifyWithNonExistingKey() {
    final Cache<Integer, Integer> cache = createCache(16);
    cache.get(0, __ -> 100);
    cache.get(1, __ -> 101);
    cache.invalidate(2);

    assertThat(cache.size()).isEqualTo(2);
    assertThat(cache.getCached(0)).contains(100);
    assertThat(cache.getCached(1)).contains(101);
  }

  @Test
  void get_shouldEvictAnItemWhenCapacityIsReached() {
    final int maxCacheSize = 16;
    final Cache<Integer, Integer> cache = createCache(maxCacheSize);

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
    final int maxCacheSize = 16;
    final Cache<Integer, Integer> sourceCache = createCache(maxCacheSize);
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

  @Test
  void clear_shouldRemoveAllEntries() {
    final Cache<Integer, Integer> cache = createCache(100);
    for (int i = 0; i < 100; i++) {
      cache.get(i, key -> key);
    }
    assertThat(cache.size()).isEqualTo(100);

    cache.clear();
    assertThat(cache.size()).isEqualTo(0);
    assertThat(cache.getCached(50)).isEmpty();
  }

  @Test
  void invalidateWithNewValue_shouldReplaceValue() {
    final Cache<Integer, Integer> cache = createCache(16);
    cache.get(1, __ -> 100);
    assertThat(cache.getCached(1)).contains(100);

    cache.invalidateWithNewValue(1, 200);
    assertThat(cache.getCached(1)).contains(200);
  }

  @Test
  void unboundedCache_shouldGrowIndefinitelyWithoutEviction() {
    final Cache<Integer, Integer> unboundedCache = createUnboundedCache();
    if (unboundedCache == null) {
      return; // Skip if unbounded not supported
    }

    final int largeSize = 10000;

    // add many items
    for (int i = 0; i < largeSize; i++) {
      unboundedCache.get(i, key -> key);
    }

    // verify all items are still in the cache (no eviction)
    assertThat(unboundedCache.size()).isEqualTo(largeSize);
    for (int i = 0; i < largeSize; i++) {
      assertThat(unboundedCache.getCached(i)).as("Item %d should still be in cache", i).contains(i);
    }
  }

  @Test
  void unboundedCache_copyShouldPreserveUnboundedBehavior() {
    final Cache<Integer, Integer> unboundedCache = createUnboundedCache();
    if (unboundedCache == null) {
      return; // Skip if unbounded not supported
    }

    final int itemCount = 1000;

    // populate the cache
    for (int i = 0; i < itemCount; i++) {
      unboundedCache.get(i, key -> key);
    }

    // test copy preserves unbounded behavior
    final Cache<Integer, Integer> copiedCache = unboundedCache.copy();
    assertThat(copiedCache.size()).isEqualTo(itemCount);

    // add more items to copied cache - should grow without eviction
    for (int i = itemCount; i < itemCount * 2; i++) {
      copiedCache.get(i, key -> key);
    }
    assertThat(copiedCache.size()).isEqualTo(itemCount * 2);

    // verify all items are still present
    for (int i = 0; i < itemCount * 2; i++) {
      assertThat(copiedCache.getCached(i)).isPresent();
    }

    // original cache should be unaffected
    assertThat(unboundedCache.size()).isEqualTo(itemCount);
  }
}
