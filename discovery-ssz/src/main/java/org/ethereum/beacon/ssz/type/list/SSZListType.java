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

package org.ethereum.beacon.ssz.type.list;

import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.SSZListAccessor;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.type.SSZHomoCompositeType;
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.TypeResolver;

/**
 * Represent specific SSZ List or Vector type with child elements of specific type which defined as
 * 'ordered variable-length (for List) or fixed-length (for Vector) homogenous collection of values
 * ' by the <a
 * href="https://github.com/ethereum/eth2.0-specs/blob/dev/specs/simple-serialize.md#composite-types">
 * SSZ spec</a>
 */
public class SSZListType implements SSZHomoCompositeType {

  private final SSZField descriptor;
  private final TypeResolver typeResolver;
  private final SSZListAccessor accessor;
  private final int vectorLength;
  private final long maxSize;

  private SSZType elementType;

  public SSZListType(
      SSZField descriptor,
      TypeResolver typeResolver,
      SSZListAccessor accessor,
      int vectorLength,
      long maxSize) {
    if (vectorLength > VARIABLE_SIZE && maxSize > VARIABLE_SIZE) {
      throw new RuntimeException("Only vectorLength or maxSize should be set at time");
    }
    this.descriptor = descriptor;
    this.typeResolver = typeResolver;
    this.accessor = accessor;
    this.vectorLength = vectorLength;
    this.maxSize = maxSize;
  }

  @Override
  public Type getType() {
    return vectorLength > VARIABLE_SIZE ? Type.VECTOR : Type.LIST;
  }

  /**
   * If this type represents SSZ Vector then this method returns its length.
   *
   * @see SSZ#vectorLength()
   * @see SSZ#vectorLengthVar()
   */
  public int getVectorLength() {
    return vectorLength;
  }

  @Override
  public int getSize() {
    if (getType() == Type.LIST || getElementType().isVariableSize()) {
      return VARIABLE_SIZE;
    }
    return getElementType().getSize() * getVectorLength();
  }

  public boolean isVector() {
    return getVectorLength() > VARIABLE_SIZE;
  }

  public long getMaxSize() {
    return maxSize;
  }

  /** Returns the {@link SSZType} of this list elements */
  @Override
  public SSZType getElementType() {
    if (elementType == null) {
      elementType =
          typeResolver.resolveSSZType(getAccessor().getListElementType(getTypeDescriptor()));
    }
    return elementType;
  }

  public SSZListAccessor getAccessor() {
    return accessor;
  }

  @Override
  public SSZField getTypeDescriptor() {
    return descriptor;
  }

  /** Indicates list bit type */
  public boolean isBitType() {
    return false;
  }
}
