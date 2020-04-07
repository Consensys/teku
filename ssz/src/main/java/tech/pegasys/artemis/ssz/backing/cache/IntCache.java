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

package tech.pegasys.artemis.ssz.backing.cache;

import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Optimized int keys cache. Eliminate int boxing/unboxing
 *
 * @param <V> type of values
 */
public interface IntCache<V> extends Cache<Integer, V> {
  /**
   * Queries value from the cache. If it's not found there, fallback function is used to calculate
   * value. After calculation result is put in cache and returned.
   *
   * @param key Key to query
   * @param fallback Fallback function for calculation of the result in case of missed cache entry
   * @return expected value result for provided key
   */
  V getInt(int key, IntFunction<V> fallback);

  @Override
  default V get(Integer key, Function<Integer, V> fallback) {
    return getInt(key, value -> fallback.apply(key));
  }

  @Override
  IntCache<V> copy();

  @Override
  default IntCache<V> transfer() {
    return copy();
  }

  /** Removes cache entry */
  void invalidateInt(int key);

  @Override
  default void invalidate(Integer key) {
    invalidateInt(key);
  }

  default void invalidateWithNewValueInt(int key, V newValue) {
    invalidateInt(key);
    getInt(key, k -> newValue);
  }

  @Override
  default void invalidateWithNewValue(Integer key, V newValue) {
    invalidateWithNewValueInt(key, newValue);
  }

  /** Clears all cached values */
  @Override
  void clear();
}
