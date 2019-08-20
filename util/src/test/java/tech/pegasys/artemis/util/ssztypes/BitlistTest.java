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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;

class BitlistTest {

  @Test
  void initTest() {
    Bitlist bitlist = new Bitlist(10);
    Assertions.assertEquals(bitlist.getBit(0), 0);
    Assertions.assertEquals(bitlist.getBit(9), 0);
  }

  @Test
  void setTest() {
    Bitlist bitlist = new Bitlist(10);
    bitlist.setBit(1);
    bitlist.setBit(3);
    bitlist.setBit(8);

    Assertions.assertEquals(bitlist.getBit(0), 0);
    Assertions.assertEquals(bitlist.getBit(1), 1);
    Assertions.assertEquals(bitlist.getBit(3), 1);
    Assertions.assertEquals(bitlist.getBit(4), 0);
    Assertions.assertEquals(bitlist.getBit(8), 1);
  }
}
