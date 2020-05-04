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

package tech.pegasys.teku.ssz.backing;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents composite mutable view which has descendant views
 *
 * @param <ChildType> the type of children
 */
public interface CompositeViewWrite<ChildType> extends ViewWrite, CompositeViewRead<ChildType> {

  /**
   * Sets the function which should called by the implementation on any changes in this view or its
   * descendant views This is to propagate changes up in the data hierarchy from child mutable views
   * to parent mutable views.
   *
   * @param listener listener to be called with this view instance as a parameter
   */
  void setInvalidator(Consumer<ViewWrite> listener);

  /**
   * Sets the child at index If the index == size() and the structure is extendable (e.g. List) then
   * this is treated as `append()` operation and the size incremented. In the latter case `size`
   * should be less than `maxSize`
   *
   * @throws IndexOutOfBoundsException if index > size() or if index == size() but size() == maxSize
   */
  void set(int index, ChildType value);

  /**
   * Similar to {@link #set(int, Object)} but using modifier function which may consider old value
   * to calculate new value The implementation may potentially optimize this case
   */
  default void update(int index, Function<ChildType, ChildType> mutator) {
    set(index, mutator.apply(get(index)));
  }
}
