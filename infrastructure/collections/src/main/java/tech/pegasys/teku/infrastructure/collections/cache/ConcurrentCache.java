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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Simple unbounded concurrent cache backed by ConcurrentHashMap with key interning. Provides
 * non-blocking access without the overhead of eviction policies. Suitable for caches that grow
 * indefinitely and don't need LRU/LFU eviction.
 *
 * <p>Key interning ensures that only one instance of each unique key exists in memory, preventing
 * duplicate key objects and reducing memory usage. This is critical for large caches with
 * heavyweight key objects like BLSPublicKey.
 *
 * <p>Use this for unbounded caches on the hot path where lock contention is a concern.
 *
 * @param <K> Keys type
 * @param <V> Values type
 */
public class ConcurrentCache<K, V> implements Cache<K, V> {

  private final ConcurrentHashMap<K, V> cache;
  private final ConcurrentHashMap<K, K> keyInterner;

  private ConcurrentCache(
      final ConcurrentHashMap<K, V> cache, final ConcurrentHashMap<K, K> keyInterner) {
    this.cache = cache;
    this.keyInterner = keyInterner;
  }

  /**
   * Creates a new unbounded concurrent cache with key interning.
   *
   * @return A new instance of ConcurrentCache.
   */
  public static <K, V> ConcurrentCache<K, V> create() {
    return new ConcurrentCache<>(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
  }

  /**
   * Interns the key to ensure only one instance exists in memory. This is critical for preventing
   * duplicate key objects.
   */
  private K internKey(final K key) {
    return keyInterner.computeIfAbsent(key, k -> k);
  }

  @Override
  public V get(final K key, final Function<K, V> fallback) {
    final K canonicalKey = internKey(key);
    return cache.computeIfAbsent(canonicalKey, fallback);
  }

  @Override
  public Optional<V> getCached(final K key) {
    // For reads, try with the provided key first (fast path if it's already canonical)
    V value = cache.get(key);
    if (value != null) {
      return Optional.of(value);
    }
    // Try with canonical key (in case a different instance was used to store it)
    final K canonicalKey = keyInterner.get(key);
    if (canonicalKey != null) {
      value = cache.get(canonicalKey);
    }
    return Optional.ofNullable(value);
  }

  @Override
  public void invalidate(final K key) {
    final K canonicalKey = keyInterner.get(key);
    if (canonicalKey != null) {
      cache.remove(canonicalKey);
      keyInterner.remove(canonicalKey);
    }
  }

  @Override
  public void invalidateWithNewValue(final K key, final V newValue) {
    final K canonicalKey = internKey(key);
    cache.put(canonicalKey, newValue);
  }

  @Override
  public void clear() {
    cache.clear();
    keyInterner.clear();
  }

  @Override
  public int size() {
    return cache.size();
  }

  @Override
  public Cache<K, V> copy() {
    final ConcurrentHashMap<K, V> newCache = new ConcurrentHashMap<>(cache);
    final ConcurrentHashMap<K, K> newKeyInterner = new ConcurrentHashMap<>(keyInterner);
    return new ConcurrentCache<>(newCache, newKeyInterner);
  }
}
