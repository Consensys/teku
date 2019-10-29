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

package org.ethereum.beacon.ssz.access;

import org.ethereum.beacon.ssz.access.SSZCompositeAccessor.CompositeInstanceAccessor;
import org.ethereum.beacon.ssz.type.SSZType;

/**
 * Handles collections of homogeneous objects (e.g. java array, {@link java.util.List}, etc) which
 * includes both ssz types: vectors and lists List accessor is responsible of accessing list
 * elements, their type and new instance creation
 *
 * <p>This interface also serves as its own {@link CompositeInstanceAccessor} since there is
 * normally no information to cache about the list type (as opposed to {@link SSZContainerAccessor})
 */
public interface SSZListAccessor extends SSZCompositeAccessor, CompositeInstanceAccessor {

  @Override
  int getChildrenCount(Object value);

  @Override
  Object getChildValue(Object value, int idx);

  /**
   * Given the list type returns the type descriptor of its elements
   *
   * @param listTypeDescriptor
   * @return List elements type
   */
  SSZField getListElementType(SSZField listTypeDescriptor);

  @Override
  ListInstanceBuilder createInstanceBuilder(SSZType sszType);

  @Override
  default CompositeInstanceAccessor getInstanceAccessor(SSZField compositeDescriptor) {
    return this;
  }

  interface ListInstanceBuilder extends CompositeInstanceBuilder {

    /** Appends a new child to the building list */
    void addChild(Object childValue);
  }
}
