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

/** Base class for mutable containers which are able to return mutable children by reference */
public interface ContainerViewWriteRef
    extends CompositeViewWriteRef<ViewRead, ViewWrite>, ContainerViewWrite {

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  // container is heterogeneous by its nature so making unsafe cast here
  // is more convenient and is not less safe
  default <W extends ViewWrite> W getAnyByRef(int index) {
    return (W) getByRef(index);
  }
}
