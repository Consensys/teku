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
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.ViewUtils;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

class BitvectorTest {

  private static int testBitvectorLength = 4;

  private static Bitvector createBitvector() {
    Bitvector bitvector = new Bitvector(testBitvectorLength);
    bitvector.setBit(0);
    bitvector.setBit(3);
    return bitvector;
  }

  @Test
  void initTest() {
    Bitvector bitvector = new Bitvector(10);
    Assertions.assertEquals(bitvector.getBit(0), 0);
    Assertions.assertEquals(bitvector.getBit(9), 0);
  }

  @Test
  void setTest() {
    Bitvector bitvector = new Bitvector(10);
    bitvector.setBit(1);
    bitvector.setBit(3);
    bitvector.setBit(8);

    Assertions.assertEquals(bitvector.getBit(0), 0);
    Assertions.assertEquals(bitvector.getBit(1), 1);
    Assertions.assertEquals(bitvector.getBit(3), 1);
    Assertions.assertEquals(bitvector.getBit(4), 0);
    Assertions.assertEquals(bitvector.getBit(8), 1);
  }

  @Test
  void serializationTest() {
    Bitvector bitvector = createBitvector();

    Bytes bitvectorSerialized = bitvector.serialize();
    Assertions.assertEquals(bitvectorSerialized.toHexString(), "0x09");
  }

  @Test
  void deserializationTest() {
    Bitvector bitvector = createBitvector();

    Bytes bitvectorSerialized = bitvector.serialize();
    Bitvector newBitvector = Bitvector.fromBytes(bitvectorSerialized, testBitvectorLength);
    Assertions.assertEquals(bitvector, newBitvector);
  }

  @Test
  void bitlistHashTest() {
    Bitlist bitlist =
        new Bitlist(Constants.MAX_VALIDATORS_PER_COMMITTEE, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    for (int i = 0; i < 44; i++) {
      bitlist.setBit(i);
    }
    Bytes32 hashOld = HashTreeUtil.hash_tree_root_bitlist(bitlist);

    ListViewRead<BitView> bitlistView = ViewUtils.createBitlistView(bitlist);
    Bytes32 hashNew = bitlistView.hashTreeRoot();

    Assertions.assertEquals(hashOld, hashNew);
  }
}
