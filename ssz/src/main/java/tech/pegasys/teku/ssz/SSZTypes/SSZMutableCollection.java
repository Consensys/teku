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

package tech.pegasys.teku.ssz.SSZTypes;

import java.util.function.Function;

public interface SSZMutableCollection<E> extends SSZImmutableCollection<E> {

  /**
   * Removes all of the elements from this collection (optional operation). The collection will be
   * empty after this method returns.
   *
   * @throws UnsupportedOperationException if the {@code clear} operation is not supported by this
   *     collection
   */
  void clear();

  /**
   * Replaces the element at the specified position in this list with the specified element
   * (optional operation).
   *
   * @param index index of the element to replace
   * @param element element to be stored at the specified position
   */
  void set(int index, E element);

  default void setAll(SSZImmutableCollection<? extends E> other) {
    clear();
    for (int i = 0; i < other.size(); i++) {
      set(i, other.get(i));
    }
  }

  default void update(int index, Function<E, E> updater) {
    set(index, updater.apply(get(index)));
  }
}
