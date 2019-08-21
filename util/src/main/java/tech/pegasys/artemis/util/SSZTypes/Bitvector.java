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

import org.apache.tuweni.bytes.Bytes;

import java.util.Arrays;
import java.util.stream.IntStream;

import static java.util.Objects.isNull;

public class Bitvector {

  private int size;
  private byte[] byteArray;

  public Bitvector(int size) {
    this.byteArray = new byte[size];
    this.size = size;
  }

  public Bitvector(byte[] bitlist) {
    this.byteArray = bitlist;
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
    int len = byteArray.length;
    byte[] array = new byte[(len / 8) + 1];
    IntStream.range(0, len).forEach(i ->
            array[i / 8] |= (((int) this.byteArray[i]) << (i % 8))
    );
    return Bytes.wrap(array);
  }

  public static Bitvector fromBytes(Bytes bytes, int size) {
    byte[] byteArray = new byte[size];

    for (int i = size - 1; i >= 0; i--) {
      if (((bytes.get(i / 8) >>> (i % 8)) & 0x01) == 1) {
        byteArray[i] = 1;
      }
    }

    return new Bitvector(byteArray);
  }

  public Bitvector copy() {
    return new Bitvector(this.getByteArray());
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
