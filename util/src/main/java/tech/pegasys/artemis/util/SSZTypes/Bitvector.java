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
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;

public class Bitvector {

  private int size;
  private byte[] byteArray;

  public Bitvector(int size) {
    this.byteArray = new byte[size];
    this.size = size;
  }

  public Bitvector(byte[] byteArray, int size) {
    this.byteArray = byteArray;
    this.size = size;
  }

  public void setBit(int i) {
    this.byteArray[i] = 1;
  }

  public int getBit(int i) {
    return byteArray[i];
  }

  public int getSize() {
    return size;
  }

  public byte[] getByteArray() {
    return byteArray;
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    byte[] array = new byte[(size + 7) / 8];
    IntStream.range(0, size).forEach(i -> array[i / 8] |= (((int) this.byteArray[i]) << (i % 8)));
    return Bytes.wrap(array);
  }

  public static Bitvector fromBytes(Bytes bytes, int size) {
    byte[] byteArray = new byte[size];

    for (int i = size - 1; i >= 0; i--) {
      if (((bytes.get(i / 8) >>> (i % 8)) & 0x01) == 1) {
        byteArray[i] = 1;
      }
    }

    return new Bitvector(byteArray, size);
  }

  public Bitvector rightShift(int i) {
    int length = this.getSize();
    Bitvector newBitvector = new Bitvector(new byte[length], length);
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
    return Arrays.hashCode(byteArray);
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
