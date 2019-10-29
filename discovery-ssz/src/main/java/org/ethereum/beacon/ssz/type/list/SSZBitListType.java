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
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.TypeResolver;

/**
 * {@link tech.pegasys.artemis.util.collections.Bitlist} and {@link
 * tech.pegasys.artemis.util.collections.Bitvector}
 *
 * <p>It's like a list/vector, but we have elements inside that are smaller than our usual elements:
 * bits instead of bytes. So we mimic old interfaces in bytes and add some new interfaces in bits
 */
public class SSZBitListType extends SSZListType {

  public SSZBitListType(
      SSZField descriptor,
      TypeResolver typeResolver,
      SSZListAccessor accessor,
      int vectorLength,
      long maxSize) {
    super(descriptor, typeResolver, accessor, vectorLength, maxSize);
  }

  /**
   * Returns byte size of Bitlist/Bitvector which is not the same that number of elements (bits) in
   * it. Its a length of list when measured in elements recognizable by SSZ - bytes. Use {@link
   * #getBitSize()} for bit size
   */
  @Override
  public int getVectorLength() {
    return (int) fromAtomicSize(super.getVectorLength());
  }

  public int getBitSize() {
    return super.getVectorLength();
  }

  /** Bits -> Bytes */
  private long fromAtomicSize(long atomicSize) {
    // List requires one more bit for size info, vector is fixed size
    int addon = getType() == Type.LIST ? 8 : 7;
    return atomicSize == SSZType.VARIABLE_SIZE ? atomicSize : (atomicSize + addon) / 8;
  }

  /**
   * Returns maximum byte size of Bitlist/Bitvector which is not the same that maximum number of
   * elements (bits) in it. Its a maximum size of elements recognizable by SSZ - bytes. Use {@link
   * #getMaxBitSize()} for maximum bit size
   */
  @Override
  public long getMaxSize() {
    return fromAtomicSize(super.getMaxSize());
  }

  public long getMaxBitSize() {
    return super.getMaxSize();
  }

  @Override
  public boolean isBitType() {
    return true;
  }
}
