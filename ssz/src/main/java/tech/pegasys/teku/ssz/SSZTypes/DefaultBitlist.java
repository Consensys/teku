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

package tech.pegasys.teku.ssz.SSZTypes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;

public class DefaultBitlist implements MutableBitlist {

  private final BitSet data;
  private final int size;
  private final long maxSize;

  public DefaultBitlist(int arraySize, long maxSize) {
    this.size = arraySize;
    this.data = new BitSet(arraySize);
    this.maxSize = maxSize;
  }

  public DefaultBitlist(DefaultBitlist bitlist) {
    this.size = bitlist.size;
    this.data = (BitSet) bitlist.data.clone();
    this.maxSize = bitlist.getMaxSize();
  }

  private DefaultBitlist(int size, BitSet data, long maxSize) {
    this.size = size;
    this.data = data;
    this.maxSize = maxSize;
  }

  @Override
  public void setBit(int i) {
    checkElementIndex(i, size);
    data.set(i);
  }

  @Override
  public void setBits(int... indexes) {
    for (int i : indexes) {
      setBit(i);
    }
  }

  @Override
  public boolean getBit(int i) {
    checkElementIndex(i, size);
    return data.get(i);
  }

  @Override
  public int getBitCount() {
    return data.cardinality();
  }

  @Override
  public boolean intersects(Bitlist other) {
    if (other instanceof DefaultBitlist) {
      return data.intersects(((DefaultBitlist) other).data);
    } else {
      return streamAllSetBits().anyMatch(other::getBit);
    }
  }

  @Override
  public boolean isSuperSetOf(final Bitlist other) {
    return other.streamAllSetBits().allMatch(this::getBit);
  }

  @Override
  public List<Integer> getAllSetBits() {
    final List<Integer> setBits = new ArrayList<>();
    for (int i = data.nextSetBit(0); i >= 0; i = data.nextSetBit(i + 1)) {
      setBits.add(i);
    }
    return setBits;
  }

  @Override
  public IntStream streamAllSetBits() {
    return data.stream();
  }

  @Override
  public void setAllBits(Bitlist other) {
    if (other.getCurrentSize() > getCurrentSize()) {
      throw new IllegalArgumentException(
          "Argument bitfield size is greater: "
              + other.getCurrentSize()
              + " > "
              + getCurrentSize());
    }

    if (other instanceof DefaultBitlist) {
      data.or(((DefaultBitlist) other).data);
    } else {
      other.streamAllSetBits().forEach(this::setBit);
    }
  }

  @Override
  public long getMaxSize() {
    return maxSize;
  }

  @Override
  public int getCurrentSize() {
    return size;
  }

  @Override
  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    int len = size;
    byte[] array = new byte[sszSerializationLength(len)];
    IntStream.range(0, len).forEach(i -> array[i / 8] |= ((data.get(i) ? 1 : 0) << (i % 8)));
    array[len / 8] |= 1 << (len % 8);
    return Bytes.wrap(array);
  }

  public static int sszSerializationLength(final int size) {
    return (size / 8) + 1;
  }

  public static Bitlist fromBytes(Bytes bytes, long maxSize) {
    int numBytes = bytes.size();
    checkArgument(numBytes > 0, "Bitlist must contain at least one byte");
    checkArgument(bytes.get(numBytes - 1) != 0, "Bitlist data must contain end marker bit");
    int leadingBitIndex = 0;
    while ((bytes.get(numBytes - 1) >>> (7 - leadingBitIndex)) % 2 == 0) {
      leadingBitIndex++;
    }

    int bitlistSize = (7 - leadingBitIndex) + (8 * (numBytes - 1));
    BitSet byteArray = new BitSet(bitlistSize);

    for (int i = bitlistSize - 1; i >= 0; i--) {
      if (((bytes.get(i / 8) >>> (i % 8)) & 0x01) == 1) {
        byteArray.set(i);
      }
    }

    return new DefaultBitlist(bitlistSize, byteArray, maxSize);
  }

  @Override
  public MutableBitlist copy() {
    return new DefaultBitlist(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DefaultBitlist bitlist = (DefaultBitlist) o;
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
