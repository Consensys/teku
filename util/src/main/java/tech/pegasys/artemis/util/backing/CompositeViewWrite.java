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

package tech.pegasys.artemis.util.backing;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents composite mutable view which has descendant views
 * @param <R> the type of children
 */
public interface CompositeViewWrite<R> extends ViewWrite, CompositeViewRead<R> {

  /**
   * Sets the function which should called by the implementation on
   * any changes in this view or its descendant views
   * @param listener listener to ba called with this view instance as a parameter
   */
  void setInvalidator(Consumer<ViewWrite> listener);

  /**
   * Sets the child at index
   * If the index == size() and the structure is extendable (e.g. List) then this is
   * treated as `append()` operation
   * @throws IllegalArgumentException if index > size() or if index == size() but size() == maxSize
   */
  void set(int index, R value);

  /**
   * Similar to {@link #set(int, Object)} but using modifier function
   * The implementation may potentially optimize this case
   */
  default void update(int index, Function<R, R> mutator) {
    set(index, mutator.apply(get(index)));
  }
}
