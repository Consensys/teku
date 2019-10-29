/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.util.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Cache made around LRU-map with fixed size, removing eldest entries (by added) when the space is
 * over
 *
 * @param <K> Keys type
 * @param <V> Values type
 */
public class LRUCache<K, V> implements Cache<K, V> {

  private final Map<K, V> cacheData;

  private final AtomicLong hits = new AtomicLong(0);
  private final AtomicLong queries = new AtomicLong(0);

  /**
   * Creates cache
   *
   * @param capacity Size of the cache
   */
  public LRUCache(int capacity) {
    this.cacheData =
        Collections.synchronizedMap(
            new LinkedHashMap<K, V>(capacity + 1, .75F, true) {
              // This method is called just after a new entry has been added
              public boolean removeEldestEntry(Map.Entry eldest) {
                return size() > capacity;
              }
            });
  }

  /**
   * Queries value from the cache. If it's not found there, fallback function is used to calculate
   * value. After calculation result is put in cache and returned.
   *
   * @param key Key to query
   * @param fallback Fallback function for calculation of the result in case of missed cache entry
   * @return expected value result for provided key
   */
  @Override
  public V get(K key, Function<K, V> fallback) {
    V result = cacheData.get(key);
    queries.incrementAndGet();

    if (result == null) {
      result = fallback.apply(key);
      cacheData.put(key, result);
    } else {
      hits.incrementAndGet();
    }

    return result;
  }

  public Optional<V> getExisting(K key) {
    return Optional.ofNullable(cacheData.get(key));
  }

  public long getHits() {
    return hits.get();
  }

  public long getQueries() {
    return queries.get();
  }

  public double getHitRatio() {
    return hits.doubleValue() / queries.doubleValue();
  }
}
