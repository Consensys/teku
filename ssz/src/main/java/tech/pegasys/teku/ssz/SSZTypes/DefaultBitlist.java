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

import static com.google.common.base.Preconditions.checkElementIndex;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;

class DefaultBitlist implements MutableBitlist {

  private final BitSet data;
  private final int size;
  private final long maxSize;

  DefaultBitlist(BitSet data, int size, long maxSize) {
    this.data = data;
    this.size = size;
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
    byte[] array = new byte[Bitlist.sszSerializationLength(len)];
    IntStream.range(0, len).forEach(i -> array[i / 8] |= ((data.get(i) ? 1 : 0) << (i % 8)));
    array[len / 8] |= 1 << (len % 8);
    return Bytes.wrap(array);
  }

  @Override
  public MutableBitlist copy() {
    return new DefaultBitlist((BitSet) data.clone(), getCurrentSize(), getMaxSize());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }

    return Bitlist.equals(this, (Bitlist) o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Bitlist.hashBits(this), size, maxSize);
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
