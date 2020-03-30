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

package tech.pegasys.artemis.util.SSZTypes;

import static java.util.Objects.isNull;

import java.util.Arrays;
import java.util.BitSet;
import org.apache.tuweni.bytes.Bytes;

public class Bitvector {

  private final int size;
  private BitSet bitSet;

  public Bitvector(int size) {
    this.size = size;
    this.bitSet = new BitSet(size);
  }

  public Bitvector(byte[] byteArray, int size) {
    this.bitSet = BitSet.valueOf(byteArray);
    this.size = size;
  }

  public Bitvector(byte[] byteArray) {
    this.bitSet = BitSet.valueOf(byteArray);
    this.size = bitSet.size();
  }

  public Bitvector(BitSet set, int size) {
    this.bitSet = set;
    this.size = size;
  }

  public void setBit(int i) {
    this.bitSet.set(i);
  }

  public int getBit(int i) {
    return this.bitSet.get(i) ? 1 : 0;
  }

  public int getSize() {
    return size;
  }

  public byte[] getByteArray() {
    return bitSet.toByteArray();
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    return Bytes.wrap(bitSet.toByteArray());
  }

  public static Bitvector fromBytes(Bytes bytes, int size) {
    return new Bitvector(bytes.toArray(), size);
  }

  public Bitvector rightShift(int i) {
    int length = this.getSize();
    Bitvector newBitvector = new Bitvector(length);
    for (int j = 0; j < length - i; j++) {
      if (this.getBit(j) == 1) {
        newBitvector.setBit(j + i);
      }
    }
    return newBitvector;
  }

  public Bitvector copy() {
    return new Bitvector(this.getByteArray(), this.getSize());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(this.getByteArray());
  }

  @Override
  public boolean equals(Object obj) {
    if (isNull(obj)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Bitvector)) {
      return false;
    }
    Bitvector other = (Bitvector) obj;
    return Arrays.equals(this.getByteArray(), other.getByteArray());
  }
}
