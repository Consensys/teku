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

public class Bitlist {

  private BitSet bitSet;
  private long maxSize;
  private int arraySize;

  public Bitlist(int arraySize, long maxSize) {
    this.bitSet = new BitSet(arraySize);
    this.arraySize = arraySize;
    this.maxSize = maxSize;
  }

  public Bitlist(final Bitlist bitlist) {
    this.bitSet = bitlist.bitSet;
    this.arraySize = bitlist.arraySize;
    this.maxSize = bitlist.getMaxSize();
  }

  public Bitlist(byte[] byteArray) {
    this.bitSet = BitSet.valueOf(byteArray);
    this.arraySize = bitSet.size();
    this.maxSize = bitSet.size();
  }

  public Bitlist(byte[] bitlist, long maxSize) {
    this.bitSet = BitSet.valueOf(bitlist);
    this.maxSize = maxSize;
    this.arraySize = (int)maxSize;
  }

  public Bitlist(BitSet randomBitSet, int n) {
    this.bitSet = randomBitSet;
    this.arraySize = n;
    this.maxSize = n;
  }

  public void setBit(int i) {
    this.bitSet.set(i);
  }

  public int getBit(int i) {
    return this.bitSet.get(i) ? 1 : 0;
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
    return this.bitSet.toByteArray();
  }

  public long getMaxSize() {
    return maxSize;
  }

  public int getCurrentSize() {
    return this.arraySize;
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    return Bytes.wrap(this.bitSet.toByteArray());
  }

  public static Bitlist fromBytes(Bytes bytes, long maxSize) {
    return new Bitlist(bytes.toArray(), maxSize);
  }

  public Bitlist copy() {
    return new Bitlist(this);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(this.bitSet.toByteArray());
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
