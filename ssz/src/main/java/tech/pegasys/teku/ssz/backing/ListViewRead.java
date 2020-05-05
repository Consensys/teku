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

import tech.pegasys.teku.ssz.backing.type.ListViewType;

/**
 * Immutable List view
 *
 * @param <ElementType> Type of list elements
 */
public interface ListViewRead<ElementType extends ViewRead> extends CompositeViewRead<ElementType> {

  @Override
  ListViewWrite<ElementType> createWritableCopy();

  @Override
  ListViewType<ElementType> getType();

  /** Returns the number of elements in this list */
  @Override
  int size();

  default boolean isEmpty() {
    return size() == 0;
  }
}
