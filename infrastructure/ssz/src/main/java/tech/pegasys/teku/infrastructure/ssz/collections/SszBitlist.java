/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.ssz.collections;

import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBitlistImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;

/** Specialized implementation of {@code SszList<SszBit>} */
public interface SszBitlist extends SszPrimitiveList<Boolean, SszBit> {

  static SszBitlist nullableOr(
      @Nullable SszBitlist bitlist1OrNull, @Nullable SszBitlist bitlist2OrNull) {
    return SszBitlistImpl.nullableOr(bitlist1OrNull, bitlist2OrNull);
  }

  @Override
  default SszMutablePrimitiveList<Boolean, SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  default boolean isWritableSupported() {
    return false;
  }

  @Override
  SszBitlistSchema<? extends SszBitlist> getSchema();

  // Bitlist methods

  /**
   * Performs a logical OR of this bit list with the bit list argument.
   *
   * @throws IllegalArgumentException if {@code other.getSize() > this.getSize()}
   */
  SszBitlist or(SszBitlist other);

  /** Returns individual bit value */
  boolean getBit(int i);

  /** Returns the number of bits set to {@code true} in this {@code SszBitlist}. */
  int getBitCount();

  /**
   * Returns {@code true} if the specified {@link SszBitlist} has any bits set to true that are also
   * set to true in this {@link SszBitlist}.
   */
  boolean intersects(SszBitlist other);

  /**
   * Returns {@code true} if this {@link SszBitlist} has all bits set to true that are set to true
   * in the {@link SszBitlist} argument.
   */
  boolean isSuperSetOf(SszBitlist other);

  /** Returns indexes of all bits set in this {@link SszBitlist} */
  List<Integer> getAllSetBits();

  /** Streams indexes of all bits set in this {@link SszBitlist} */
  default IntStream streamAllSetBits() {
    return getAllSetBits().stream().mapToInt(i -> i);
  }

  @Override
  default Boolean getElement(int index) {
    return getBit(index);
  }
}
