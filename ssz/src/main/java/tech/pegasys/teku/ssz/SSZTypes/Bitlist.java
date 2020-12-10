/*
 * Copyright 2020 ConsenSys AG.
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

import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;

public interface Bitlist {
  static int sszSerializationLength(final int size) {
    return (size / 8) + 1;
  }

  static Bitlist fromBytes(Bytes bytes, long maxSize) {
    return MutableBitlist.fromBytes(bytes, maxSize);
  }

  static boolean equals(final Bitlist a, final Bitlist b) {
    if (a.getCurrentSize() != b.getCurrentSize() || a.getMaxSize() != b.getMaxSize()) {
      return false;
    }

    // Check each bit
    for (int i = 0; i < a.getCurrentSize(); i++) {
      if (a.getBit(i) != b.getBit(i)) {
        return false;
      }
    }

    return true;
  }

  static int hashBits(final Bitlist bitlist) {
    int result = 1;
    int size = bitlist.getCurrentSize();
    for (int i = 0; i < size; i++) {
      final int bit = bitlist.getBit(i) ? 1 : 0;
      result = 31 * result + bit;
    }
    return result;
  }

  boolean getBit(int i);

  /** @return Returns the number of bits set to {@code true} in this {@code Bitlist}. */
  int getBitCount();

  boolean intersects(Bitlist other);

  boolean isSuperSetOf(Bitlist other);

  List<Integer> getAllSetBits();

  IntStream streamAllSetBits();

  long getMaxSize();

  int getCurrentSize();

  @SuppressWarnings("NarrowingCompoundAssignment")
  default Bytes serialize() {
    int len = getCurrentSize();
    byte[] array = new byte[Bitlist.sszSerializationLength(len)];
    IntStream.range(0, len).forEach(i -> array[i / 8] |= ((getBit(i) ? 1 : 0) << (i % 8)));
    array[len / 8] |= 1 << (len % 8);
    return Bytes.wrap(array);
  }

  MutableBitlist copy();
}
