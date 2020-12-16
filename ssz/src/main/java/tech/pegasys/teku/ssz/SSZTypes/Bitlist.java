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
import org.apache.tuweni.bytes.MutableBytes;

public class Bitlist {

  private final BitSet data;
  private final int size;
  private final long maxSize;

  public Bitlist(int arraySize, long maxSize) {
    this.size = arraySize;
    this.data = new BitSet(arraySize);
    this.maxSize = maxSize;
  }

  public Bitlist(Bitlist bitlist) {
    this.size = bitlist.size;
    this.data = (BitSet) bitlist.data.clone();
    this.maxSize = bitlist.getMaxSize();
  }

  private Bitlist(int size, BitSet data, long maxSize) {
    this.size = size;
    this.data = data;
    this.maxSize = maxSize;
  }

  public void setBit(int i) {
    checkElementIndex(i, size);
    data.set(i);
  }

  public void setBits(int... indexes) {
    for (int i : indexes) {
      setBit(i);
    }
  }

  public boolean getBit(int i) {
    checkElementIndex(i, size);
    return data.get(i);
  }

  public int getBitCount() {
    return data.cardinality();
  }

  public boolean intersects(Bitlist other) {
    return data.intersects(other.data);
  }

  public boolean isSuperSetOf(final Bitlist other) {
    return other.streamAllSetBits().allMatch(this::getBit);
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

  /** Sets all bits in this bitlist which are set in the [other] list */
  public void setAllBits(Bitlist other) {
    if (other.getCurrentSize() > getCurrentSize()) {
      throw new IllegalArgumentException(
          "Argument bitfield size is greater: "
              + other.getCurrentSize()
              + " > "
              + getCurrentSize());
    }
    data.or(other.data);
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

  public static int sszSerializationLength(final int size) {
    return (size / 8) + 1;
  }

  public static Bitlist fromSszBytes(Bytes bytes, long maxSize) {
    int bitlistSize = sszGetLengthAndValidate(bytes);
    BitSet byteArray = new BitSet(bitlistSize);

    for (int i = bitlistSize - 1; i >= 0; i--) {
      if (((bytes.get(i / 8) >>> (i % 8)) & 0x01) == 1) {
        byteArray.set(i);
      }
    }

    return new Bitlist(bitlistSize, byteArray, maxSize);
  }

  public static Bytes sszTruncateLeadingBit(Bytes bytes, int length) {
    Bytes bytesWithoutLast = bytes.slice(0, bytes.size() - 1);
    if (length % 8 == 0) {
      return bytesWithoutLast;
    } else {
      int lastByte = 0xFF & bytes.get(bytes.size() - 1);
      int leadingBit = 1 << (length % 8);
      int lastByteWithoutLeadingBit = lastByte ^ leadingBit;
      return Bytes.concatenate(bytesWithoutLast, Bytes.of(lastByteWithoutLeadingBit));
    }
  }

  public static Bytes sszAppendLeadingBit(Bytes bytes, int length) {
    checkArgument(length <= bytes.size() * 8 && length > (bytes.size() - 1) * 8);
    if (length % 8 == 0) {
      return Bytes.wrap(bytes, Bytes.of(1));
    } else {
      int lastByte = 0xFF & bytes.get(bytes.size() - 1);
      int leadingBit = 1 << (length % 8);
      checkArgument((-leadingBit & lastByte) == 0, "Bits higher than length should be 0");
      int lastByteWithLeadingBit = lastByte ^ leadingBit;
      // workaround for Bytes bug. See BitlistViewTest.tuweniBytesIssue() test
      MutableBytes resultBytes = bytes.mutableCopy();
      resultBytes.set(bytes.size() - 1, (byte) lastByteWithLeadingBit);
      return resultBytes;
    }
  }

  public static int sszGetLengthAndValidate(Bytes bytes) {
    int numBytes = bytes.size();
    checkArgument(numBytes > 0, "Bitlist must contain at least one byte");
    checkArgument(bytes.get(numBytes - 1) != 0, "Bitlist data must contain end marker bit");
    int lastByte = 0xFF & bytes.get(bytes.size() - 1);
    int leadingBitIndex = Integer.bitCount(Integer.highestOneBit(lastByte) - 1);
    return leadingBitIndex + 8 * (numBytes - 1);
  }

  public Bitlist copy() {
    return new Bitlist(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Bitlist bitlist = (Bitlist) o;
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
