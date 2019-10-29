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

package org.ethereum.beacon.db.source;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Add-only list which can miss elements at some positions and its size is the maximal element index
 * Also can be treated as <code>Map&lt;Long, V&gt;</code> with maximal key tracking.
 */
public interface HoleyList<V> {

  /** Maximal index of inserted element + 1 */
  long size();

  /**
   * Put element at index <code>idx</code> Increases size if necessary If value is null nothing is
   * modified
   */
  void put(long idx, V value);

  /**
   * Returns element at index <code>idx</code> Empty instance is returned if no element with this
   * index
   */
  Optional<V> get(long idx);

  /** Puts element with index <code>size()</code> */
  default void add(V value) {
    put(size(), value);
  }

  /**
   * Handy functional method to update existing value or put a default if no value exists yet
   *
   * @return new value
   */
  default V update(long idx, Function<V, V> updater, Supplier<V> defaultValue) {
    V newVal = get(idx).map(updater).orElse(defaultValue.get());
    put(idx, newVal);
    return newVal;
  }

  /**
   * Handy functional method to update existing value
   *
   * @return new value if existed
   */
  default Optional<V> update(long idx, Function<V, V> updater) {
    return get(idx)
        .map(
            val -> {
              V newVal = updater.apply(val);
              put(idx, newVal);
              return newVal;
            });
  }
}
