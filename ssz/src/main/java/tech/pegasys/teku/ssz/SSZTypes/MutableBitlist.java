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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.BitSet;
import org.apache.tuweni.bytes.Bytes;

public interface MutableBitlist extends Bitlist {
  static MutableBitlist create(int size, long maxSize) {
    return new DefaultBitlist(new BitSet(size), size, maxSize);
  }

  static MutableBitlist fromBytes(Bytes bytes, long maxSize) {
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

    return new DefaultBitlist(byteArray, bitlistSize, maxSize);
  }

  void setBit(int i);

  void setBits(int... indexes);

  /** Sets all bits in this bitlist which are set in the [other] list */
  void setAllBits(Bitlist other);
}
