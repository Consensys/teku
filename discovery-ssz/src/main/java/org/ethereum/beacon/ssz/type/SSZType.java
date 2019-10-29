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

import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.type.list.SSZListType;

/**
 * Describes the specific SSZ type. See <a
 * href="https://github.com/ethereum/eth2.0-specs/blob/dev/specs/simple-serialize.md#typing">
 * official SSZ docs</a> for details.
 */
public interface SSZType {

  enum Type {
    /** Can be safely cast to {@link SSZBasicType} */
    BASIC,
    /** Can be safely cast to {@link SSZContainerType} or {@link SSZCompositeType} */
    CONTAINER,
    /**
     * Indicates this is List SSZ type. Can be safely cast to {@link SSZListType} or {@link
     * SSZCompositeType}
     */
    LIST,
    /**
     * Indicates this is Vector SSZ type. Can be safely cast to {@link SSZListType} or {@link
     * SSZCompositeType}
     */
    VECTOR,
    UNION
  }

  /** Constant size indicating variable size */
  int VARIABLE_SIZE = -1;

  /** See SSZ types in the {@link Type} enum */
  Type getType();

  /** Indicates this is fixed size type. */
  default boolean isFixedSize() {
    return getSize() != VARIABLE_SIZE;
  }

  /** Indicates this is variable size type. */
  default boolean isVariableSize() {
    return !isFixedSize();
  }

  /** Gets the size of this type or {@link #VARIABLE_SIZE} if the type {@link #isVariableSize()} */
  int getSize();

  /**
   * Returns the type descriptor which contains accessor specific type info
   *
   * @see org.ethereum.beacon.ssz.access.AccessorResolver
   * @see org.ethereum.beacon.ssz.access.SSZBasicAccessor
   * @see org.ethereum.beacon.ssz.access.SSZListAccessor
   * @see org.ethereum.beacon.ssz.access.SSZContainerAccessor
   */
  SSZField getTypeDescriptor();

  default String toStringHelper() {
    return "SSZType[" + getType() + ", size=" + getSize() + ", descr: " + getTypeDescriptor() + "]";
  }

  default String dumpHierarchy() {
    return dumpHierarchy("");
  }

  default String dumpHierarchy(String indent) {
    String ret = "";
    ret += indent + toStringHelper() + "\n";
    if (getType() == Type.LIST || getType() == Type.VECTOR) {
      ret += ((SSZListType) this).getElementType().dumpHierarchy(indent + "  ");
    }
    if (getType() == Type.CONTAINER) {
      for (SSZType sszType : ((SSZContainerType) this).getChildTypes()) {
        ret += sszType.dumpHierarchy(indent + "  ");
      }
    }
    return ret;
  }
}
