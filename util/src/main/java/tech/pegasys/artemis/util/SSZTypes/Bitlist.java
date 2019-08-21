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

import java.util.stream.IntStream;

public class Bitlist {

  private byte[] bitlist;

  public Bitlist(int n) {
    this.bitlist = new byte[n];
  }

  public void setBit(int i) {
    this.bitlist[i] = 1;
  }

  public int getBit(int i) {
    return bitlist[i];
  }

  @SuppressWarnings("NarrowingCompoundAssignment")
  public Bytes serialize() {
    int len = bitlist.length;
    byte[] array = new byte[(len / 8) + 1];
    IntStream.range(0, len).forEach(i -> array[i / 8] |= (this.bitlist[i] << (i % 8)));
    array[len / 8] |= 1 << (len % 8);
    return Bytes.wrap(array);
  }

  public Bytes deserialize(Bytes bytes) {
    return Bytes.EMPTY;
  }
}
