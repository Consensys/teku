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

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;

public interface SSZImmutableCollection<E> extends Iterable<E> {

  /**
   * Returns the number of elements in this list. If this list contains more than {@code
   * Integer.MAX_VALUE} elements, returns {@code Integer.MAX_VALUE}.
   *
   * @return the number of elements in this list
   */
  int size();

  /**
   * Returns {@code true} if this list contains no elements.
   *
   * @return {@code true} if this list contains no elements
   */
  default boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Returns the element at the specified position in this list.
   *
   * @param index index of the element to return
   * @return the element at the specified position in this list
   * @throws IndexOutOfBoundsException if the index is out of range ({@code index < 0 || index >=
   *     size()})
   */
  E get(int index);

  default Stream<E> stream() {
    return StreamSupport.stream(spliterator(), false);
  }

  Class<? extends E> getElementType();

  Bytes32 hash_tree_root();

  long getMaxSize();

  @Override
  default Spliterator<E> spliterator() {
    return Spliterators.spliterator(iterator(), size(), 0);
  }

  @NotNull
  @Override
  default Iterator<E> iterator() {
    return new Iterator<E>() {
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < size();
      }

      @Override
      public E next() {
        return get(index++);
      }
    };
  }

  default List<E> asList() {
    return stream().collect(Collectors.toList());
  }
}
