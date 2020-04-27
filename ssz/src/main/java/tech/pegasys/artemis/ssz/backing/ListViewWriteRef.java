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

/**
 * Represents a mutable List view which is able to return a mutable child 'by reference' Any
 * modifications made to such child are reflected in this list and its backing tree
 *
 * @param <ElementReadType> Class of immutable child views
 * @param <ElementWriteType> Class of the corresponding mutable child views
 */
public interface ListViewWriteRef<
        ElementReadType extends ViewRead, ElementWriteType extends ElementReadType>
    extends CompositeViewWriteRef<ElementReadType, ElementWriteType>,
        ListViewWrite<ElementReadType> {

  /**
   * Returns a mutable child at index 'by reference' Any modifications made to such child are
   * reflected in this structure and its backing tree
   *
   * @throws IndexOutOfBoundsException if index >= size()
   */
  @Override
  ElementWriteType getByRef(int index);

  /**
   * Appends a new empty element to the list and returns its writeable reference for modification
   */
  default ElementWriteType append() {
    @SuppressWarnings("unchecked")
    ElementReadType newElement = (ElementReadType) getType().getElementType().getDefault();
    append(newElement);
    return getByRef(size() - 1);
  }

  /** Just a functional style helper for {@link #append()} */
  default void append(Consumer<ElementWriteType> mutator) {
    mutator.accept(append());
  }
}
