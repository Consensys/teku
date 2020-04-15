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

package tech.pegasys.artemis.ssz.SSZTypes;

import static com.google.common.base.Preconditions.checkElementIndex;

import com.google.common.base.Objects;
import java.util.BitSet;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;

public class Bitvector {

  private final BitSet data;
  private final int size;

  public Bitvector(int size) {
    this.data = new BitSet(size);
    this.size = size;
  }

  public Bitvector(BitSet bitSet, int size) {
    this.data = bitSet;
    this.size = size;
  }

  public Bitvector(Bitvector bitvector) {
    this.data = (BitSet) bitvector.data.clone();
    this.size = bitvector.size;
  }

  public void setBit(int i) {
    checkElementIndex(i, size);
    data.set(i);
  }

  public boolean getBit(int i) {
    checkElementIndex(i, size);
    return data.get(i);
  }

  public int getSize() {
    return size;
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    byte[] array = new byte[(size + 7) / 8];
    IntStream.range(0, size).forEach(i -> array[i / 8] |= ((data.get(i) ? 1 : 0) << (i % 8)));
    return Bytes.wrap(array);
  }

  public static Bitvector fromBytes(Bytes bytes, int size) {
    BitSet bitset = new BitSet(size);

    for (int i = size - 1; i >= 0; i--) {
      if (((bytes.get(i / 8) >>> (i % 8)) & 0x01) == 1) {
        bitset.set(i);
      }
    }

    return new Bitvector(bitset, size);
  }

  public Bitvector rightShift(int i) {
    int length = this.getSize();
    Bitvector newBitvector = new Bitvector(getSize());
    for (int j = 0; j < length - i; j++) {
      if (this.getBit(j)) {
        newBitvector.setBit(j + i);
      }
    }
    return newBitvector;
  }

  public Bitvector copy() {
    return new Bitvector(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Bitvector)) return false;
    Bitvector bitvector = (Bitvector) o;
    return getSize() == bitvector.getSize() && Objects.equal(data, bitvector.data);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(data, getSize());
  }
}
