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

public class Bitlist {

  private byte[] byteArray;
  private long maxSize;

  public Bitlist(int arraySize, long maxSize) {
    this.byteArray = new byte[arraySize];
    this.maxSize = maxSize;
  }

  public Bitlist(Bitlist bitlist) {
    this.byteArray = new byte[bitlist.getByteArray().length];
    for (int i = 0; i < bitlist.getByteArray().length; i++) {
      this.byteArray[i] = bitlist.getByteArray()[i];
    }
    this.maxSize = bitlist.getMaxSize();
  }

  public Bitlist(byte[] bitlist, long maxSize) {
    this.byteArray = bitlist;
    this.maxSize = maxSize;
  }

  public void setBit(int i) {
    this.byteArray[i] = 1;
  }

  public int getBit(int i) {
    return byteArray[i];
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
    for (int i = 0; i < other.getCurrentSize(); i++) {
      if (other.getBit(i) > 0) {
        setBit(i);
      }
    }
  }

  public byte[] getByteArray() {
    return byteArray;
  }

  public long getMaxSize() {
    return maxSize;
  }

  public int getCurrentSize() {
    return byteArray.length;
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    int len = byteArray.length;
    byte[] array = new byte[(len / 8) + 1];
    IntStream.range(0, len).forEach(i -> array[i / 8] |= (((int) this.byteArray[i]) << (i % 8)));
    array[len / 8] |= 1 << (len % 8);
    return Bytes.wrap(array);
  }

  public static Bitlist fromBytes(Bytes bytes, long maxSize) {
    int numBytes = bytes.size();
    int leadingBitIndex = 0;
    while ((bytes.get(numBytes - 1) >>> (7 - leadingBitIndex)) % 2 == 0) {
      leadingBitIndex++;
    }

    int bitlistSize = (7 - leadingBitIndex) + (8 * (numBytes - 1));
    byte[] byteArray = new byte[bitlistSize];

    for (int i = bitlistSize - 1; i >= 0; i--) {
      if (((bytes.get(i / 8) >>> (i % 8)) & 0x01) == 1) {
        byteArray[i] = 1;
      }
    }

    return new Bitlist(byteArray, maxSize);
  }

  public Bitlist copy() {
    return new Bitlist(this);
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
    if (!(obj instanceof Bitlist)) {
      return false;
    }
    Bitlist other = (Bitlist) obj;
    return Arrays.equals(this.getByteArray(), other.getByteArray());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < getCurrentSize(); i++) {
      sb.append(getBit(i));
    }
    return sb.toString();
  }
}
