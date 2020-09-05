/*
 * Copyright 2020 ConsenSys AG.
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
import java.util.function.Function;

/**
 * Cache without cache proxying all requests to fallback function
 *
 * @param <K> Keys type
 * @param <V> Values type
 */
public class NoOpCache<K, V> implements Cache<K, V> {

  @SuppressWarnings("rawtypes")
  private static final NoOpCache INSTANCE = new NoOpCache();

  @SuppressWarnings("unchecked")
  public static <K, V> Cache<K, V> getNoOpCache() {
    return INSTANCE;
  }

  /** Creates cache */
  private NoOpCache() {}

  /**
   * Just calls fallback to calculate result and returns it as it's mock cache
   *
   * @param key Key to query
   * @param fallback Fallback function for calculation of the result
   * @return expected value result for provided key
   */
  @Override
  public V get(K key, Function<K, V> fallback) {
    return fallback.apply(key);
  }

  @Override
  public Optional<V> getCached(K key) {
    return Optional.empty();
  }

  @Override
  public Cache<K, V> copy() {
    return this;
  }

  @Override
  public void invalidate(K key) {}

  @Override
  public void clear() {}

  @Override
  public int size() {
    return 0;
  }
}
