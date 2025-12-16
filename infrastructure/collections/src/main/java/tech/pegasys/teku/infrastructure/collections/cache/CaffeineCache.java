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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Optional;
import java.util.function.Function;

/**
 * This class implements the {@link Cache} interface, serving as a superior drop-in replacement for
 * the previous synchronized-based LRUCache. It uses Caffeine's TinyLFU eviction policy, which
 * provides a better overall hit rate than traditional LRU by considering both frequency and recency
 * of access.
 *
 * @param <K> type of keys
 * @param <V> type of values
 */
public final class CaffeineCache<K, V> implements Cache<K, V> {

  private final LoadingCache<K, V> cache;
  private final Caffeine<Object, Object> builder;

  CaffeineCache(final LoadingCache<K, V> cache, final Caffeine<Object, Object> builder) {
    this.cache = cache;
    this.builder = builder;
  }

  /**
   * Factory method to create a new CaffeineCache with a specified capacity.
   *
   * @param capacity The maximum number of items the cache can hold.
   * @return A new instance of CaffeineCache.
   */
  public static <K, V> CaffeineCache<K, V> create(final int capacity) {
    final Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(capacity);
    final LoadingCache<K, V> caffeineCache =
        builder.build(
            key -> {
              throw new UnsupportedOperationException(
                  "Fallback function must be provided to get()");
            });
    return new CaffeineCache<>(caffeineCache, builder);
  }

  @Override
  public V get(final K key, final Function<K, V> fallback) {
    return cache.get(key, fallback);
  }

  @Override
  public Optional<V> getCached(final K key) {
    return Optional.ofNullable(cache.getIfPresent(key));
  }

  @Override
  public void invalidate(final K key) {
    cache.invalidate(key);
  }

  @Override
  public void invalidateWithNewValue(final K key, final V newValue) {
    cache.put(key, newValue);
  }

  @Override
  public void clear() {
    cache.invalidateAll();
  }

  @Override
  public int size() {
    return (int) cache.estimatedSize();
  }

  @Override
  public Cache<K, V> copy() {
    final LoadingCache<K, V> newCacheInstance =
        this.builder.build(
            key -> {
              throw new UnsupportedOperationException(
                  "Fallback function must be provided to get()");
            });
    newCacheInstance.putAll(this.cache.asMap());
    return new CaffeineCache<>(newCacheInstance, this.builder);
  }

  @Override
  public Cache<K, V> transfer() {
    final Cache<K, V> copiedCache = this.copy();
    this.clear();
    return copiedCache;
  }
}
