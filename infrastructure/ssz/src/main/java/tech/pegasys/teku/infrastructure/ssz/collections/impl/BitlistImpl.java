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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;

class BitlistImpl {

  public static int sszSerializationLength(final int size) {
    return (size / 8) + 1;
  }

  public static BitlistImpl fromSszBytes(Bytes bytes, long maxSize) {
    int bitlistSize = SszBitlistImpl.sszGetLengthAndValidate(bytes);
    BitSet bitSet = BitSet.valueOf(bytes.toArrayUnsafe()).get(0, bitlistSize);
    return new BitlistImpl(bitlistSize, bitSet, maxSize);
  }

  private final BitSet data;
  private final int size;
  private final long maxSize;

  public BitlistImpl(int size, long maxSize, int... bitIndexes) {
    checkArgument(size >= 0, "Negative size");
    checkArgument(maxSize >= size, "maxSize should be >= size");
    this.size = size;
    this.data = new BitSet(size);
    this.maxSize = maxSize;
    for (int bitIndex : bitIndexes) {
      checkElementIndex(bitIndex, size);
      data.set(bitIndex);
    }
  }

  private BitlistImpl(int size, BitSet data, long maxSize) {
    this.size = size;
    this.data = data;
    this.maxSize = maxSize;
  }

  /**
   * Returns new instance of this BitlistImpl with set bits from the other BitlistImpl
   *
   * @throws IllegalArgumentException if the size of the other BitlistImpl is greater than the size
   *     of this BitlistImpl
   */
  public BitlistImpl or(BitlistImpl other) {
    if (other.getCurrentSize() > getCurrentSize()) {
      throw new IllegalArgumentException(
          "Argument bitfield size is greater: "
              + other.getCurrentSize()
              + " > "
              + getCurrentSize());
    }
    BitSet newData = (BitSet) this.data.clone();
    newData.or(other.data);
    return new BitlistImpl(size, newData, maxSize);
  }

  public boolean getBit(int i) {
    checkElementIndex(i, size);
    return data.get(i);
  }

  public int getBitCount() {
    return data.cardinality();
  }

  public boolean intersects(BitlistImpl other) {
    return data.intersects(other.data);
  }

  public boolean isSuperSetOf(final BitlistImpl other) {
    return other.streamAllSetBits().allMatch(idx -> idx < getCurrentSize() && getBit(idx));
  }

  public List<Integer> getAllSetBits() {
    final List<Integer> setBits = new ArrayList<>();
    for (int i = data.nextSetBit(0); i >= 0; i = data.nextSetBit(i + 1)) {
      setBits.add(i);
    }
    return setBits;
  }

  public IntStream streamAllSetBits() {
    return data.stream();
  }

  public long getMaxSize() {
    return maxSize;
  }

  public int getCurrentSize() {
    return size;
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    int len = size;
    byte[] array = new byte[sszSerializationLength(len)];
    IntStream.range(0, len).forEach(i -> array[i / 8] |= ((data.get(i) ? 1 : 0) << (i % 8)));
    array[len / 8] |= 1 << (len % 8);
    return Bytes.wrap(array);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BitlistImpl bitlist = (BitlistImpl) o;
    return size == bitlist.size && maxSize == bitlist.maxSize && Objects.equals(data, bitlist.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, size, maxSize);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < getCurrentSize(); i++) {
      sb.append(getBit(i) ? 1 : 0);
    }
    return sb.toString();
  }
}
