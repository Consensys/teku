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

package org.ethereum.beacon.ssz.type;

import org.ethereum.beacon.ssz.access.SSZCompositeAccessor;
import org.ethereum.beacon.ssz.type.list.SSZListType;

/**
 * Common superinterface for {@link SSZListType} and {@link SSZContainerType} types which both have
 * children elements accessible by their index
 */
public interface SSZCompositeType extends SSZType {

  /**
   * Shortcut for <code>
   * this.getAccessor().getAccessor(this.getTypeDescriptor()).getChildrenCount(value)</code> Returns
   * the children count of the composite value Java representation instance
   */
  default int getChildrenCount(Object value) {
    return getAccessor().getInstanceAccessor(getTypeDescriptor()).getChildrenCount(value);
  }

  /**
   * Shortcut for <code>
   * this.getAccessor().getAccessor(this.getTypeDescriptor()).getChildValue(value, idx)</code>
   * Returns the child at index of the composite value Java representation instance
   */
  default Object getChild(Object value, int idx) {
    return getAccessor().getInstanceAccessor(getTypeDescriptor()).getChildValue(value, idx);
  }

  /** Returns the corresponding {@link SSZCompositeAccessor} instance */
  SSZCompositeAccessor getAccessor();
}
