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

import java.util.Optional;
import java.util.function.Function;

/** Checks validity of cache entry on access */
public class DebugCacheFactory implements CacheFactory {

  @Override
  public <K, V> Cache<K, V> createLRUCache(int capacity) {
    return new Cache<K, V>() {

      LRUCache<K, V> cache = new LRUCache<>(capacity);

      @Override
      public V get(K key, Function<K, V> fallback) {
        Optional<V> cacheEntry = cache.getExisting(key);
        if (cacheEntry.isPresent()) {
          V goldenVal = fallback.apply(key);
          if (!cacheEntry.get().equals(goldenVal)) {
            throw new IllegalStateException(
                "Cache broken: key="
                    + key
                    + ", cacheEntry: "
                    + cacheEntry.get()
                    + ", but should be: "
                    + goldenVal);
          }
          return goldenVal;
        } else {
          return cache.get(key, fallback);
        }
      }
    };
  }
}
