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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Unbounded cache backed by {@link ConcurrentHashMap}. Provides lock-free reads and fine-grained
 * locking on writes with minimal per-entry memory overhead. Ideal for caches that grow with the
 * validator set and never evict (e.g., validator public key lookups).
 *
 * @param <K> type of keys
 * @param <V> type of values
 */
public final class ConcurrentMapCache<K, V> implements Cache<K, V> {

  private final ConcurrentHashMap<K, V> map;

  public ConcurrentMapCache() {
    this.map = new ConcurrentHashMap<>();
  }

  private ConcurrentMapCache(final ConcurrentHashMap<K, V> map) {
    this.map = map;
  }

  @Override
  public V get(final K key, final Function<K, V> fallback) {
    return map.computeIfAbsent(key, fallback);
  }

  @Override
  public Optional<V> getCached(final K key) {
    return Optional.ofNullable(map.get(key));
  }

  @Override
  public void invalidate(final K key) {
    map.remove(key);
  }

  @Override
  public void invalidateWithNewValue(final K key, final V newValue) {
    map.put(key, newValue);
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public Cache<K, V> copy() {
    return new ConcurrentMapCache<>(new ConcurrentHashMap<>(map));
  }
}
