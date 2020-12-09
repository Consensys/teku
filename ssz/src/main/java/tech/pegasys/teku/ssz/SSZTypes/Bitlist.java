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

  boolean getBit(int i);

  int getBitCount();

  boolean intersects(Bitlist other);

  boolean isSuperSetOf(Bitlist other);

  List<Integer> getAllSetBits();

  IntStream streamAllSetBits();

  long getMaxSize();

  int getCurrentSize();

  Bytes serialize();

  MutableBitlist copy();
}
