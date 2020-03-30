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

package tech.pegasys.artemis.util.ssztypes;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;

class BitlistTest {
  private static int bitlistMaxSize = 4000;

  private static Bitlist createBitlist() {
    Bitlist bitlist = new Bitlist(18, bitlistMaxSize);
    bitlist.setBit(1);
    bitlist.setBit(4);
    bitlist.setBit(5);
    bitlist.setBit(6);
    bitlist.setBit(11);
    bitlist.setBit(12);
    bitlist.setBit(17);
    return bitlist;
  }

  @Test
  void initTest() {
    Bitlist bitlist = new Bitlist(10, bitlistMaxSize);
    Assertions.assertEquals(bitlist.getBit(0), 0);
    Assertions.assertEquals(bitlist.getBit(9), 0);
  }

  @Test
  void setTest() {
    Bitlist bitlist = new Bitlist(10, bitlistMaxSize);
    bitlist.setBit(1);
    bitlist.setBit(3);
    bitlist.setBit(8);

    Assertions.assertEquals(bitlist.getBit(0), 0);
    Assertions.assertEquals(bitlist.getBit(1), 1);
    Assertions.assertEquals(bitlist.getBit(3), 1);
    Assertions.assertEquals(bitlist.getBit(4), 0);
    Assertions.assertEquals(bitlist.getBit(8), 1);
  }

  @Test
  void serializationTest() {
    Bitlist bitlist = createBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Assertions.assertEquals(bitlistSerialized.toHexString(), "0x721802");
  }

  @Test
  void deserializationTest() {
    Bitlist bitlist = createBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Bitlist newBitlist = Bitlist.fromBytes(bitlistSerialized, bitlistMaxSize);
    Assertions.assertEquals(bitlist, newBitlist);
  }

  @Test
  void serializationTest2() {
    Bitlist bitlist = new Bitlist(9, bitlistMaxSize);
    bitlist.setBit(0);
    bitlist.setBit(3);
    bitlist.setBit(4);
    bitlist.setBit(5);
    bitlist.setBit(6);
    bitlist.setBit(7);
    bitlist.setBit(8);

    Bytes bitlistSerialized = bitlist.serialize();
    Assertions.assertEquals(Bytes.fromHexString("0xf901"), bitlistSerialized);
  }

  @Test
  void deserializationTest2() {
    Bitlist bitlist = new Bitlist(9, bitlistMaxSize);
    bitlist.setBit(0);
    bitlist.setBit(3);
    bitlist.setBit(4);
    bitlist.setBit(5);
    bitlist.setBit(6);
    bitlist.setBit(7);
    bitlist.setBit(8);

    Bitlist newBitlist = Bitlist.fromBytes(Bytes.fromHexString("0xf901"), bitlistMaxSize);
    Assertions.assertEquals(bitlist, newBitlist);
  }
}
