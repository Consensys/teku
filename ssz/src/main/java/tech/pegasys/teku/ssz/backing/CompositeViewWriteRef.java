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

/**
 * Represents a mutable composite view which is able to return a mutable child 'by reference' Any
 * modifications made to such child are reflected in this structure and its backing tree
 */
public interface CompositeViewWriteRef<
        ChildReadType extends ViewRead, ChildWriteType extends ChildReadType>
    extends CompositeViewWrite<ChildReadType> {

  /**
   * Returns a mutable child at index 'by reference' Any modifications made to such child are
   * reflected in this structure and its backing tree
   *
   * @throws IndexOutOfBoundsException if index >= size()
   */
  ChildWriteType getByRef(int index);
}
