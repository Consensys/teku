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

public class ConcurrentMapCache<K, V> implements Cache<K, V> {
  private final ConcurrentHashMap<K, V> cacheData;

  public ConcurrentMapCache() {
    this(new ConcurrentHashMap<>());
  }

  private ConcurrentMapCache(final ConcurrentHashMap<K, V> cacheData) {
    this.cacheData = cacheData;
  }

  @Override
  public V get(final K key, final Function<K, V> fallback) {
    return cacheData.computeIfAbsent(key, fallback);
  }

  @Override
  public Optional<V> getCached(final K key) {
    return Optional.ofNullable(cacheData.get(key));
  }

  @Override
  public Cache<K, V> copy() {
    return new ConcurrentMapCache<>(new ConcurrentHashMap<>(cacheData));
  }

  @Override
  public void invalidate(final K key) {
    cacheData.remove(key);
  }

  @Override
  public void invalidateWithNewValue(final K key, final V newValue) {
    cacheData.put(key, newValue);
  }

  @Override
  public void clear() {
    cacheData.clear();
  }

  @Override
  public int size() {
    return cacheData.size();
  }
}
