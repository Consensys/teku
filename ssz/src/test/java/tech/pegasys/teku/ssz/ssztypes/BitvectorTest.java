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

package tech.pegasys.teku.ssz.ssztypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.view.BasicViews.BitView;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

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
    Assertions.assertEquals(bitvector.getBit(0), false);
    Assertions.assertEquals(bitvector.getBit(9), false);
  }

  @Test
  void setTest() {
    Bitvector bitvector = new Bitvector(10);
    bitvector.setBit(1);
    bitvector.setBit(3);
    bitvector.setBit(8);

    Assertions.assertEquals(bitvector.getBit(0), false);
    Assertions.assertEquals(bitvector.getBit(1), true);
    Assertions.assertEquals(bitvector.getBit(3), true);
    Assertions.assertEquals(bitvector.getBit(4), false);
    Assertions.assertEquals(bitvector.getBit(8), true);
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
  public void deserializationEmptyBytesTest() {
    final Bitvector result = Bitvector.fromBytes(Bytes.EMPTY, 0);
    assertThat(result.getSize()).isZero();
  }

  @Test
  public void deserializationNotEnoughBytes() {
    assertThatThrownBy(() -> Bitvector.fromBytes(Bytes.of(1, 2, 3), 50))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Incorrect data size");
  }

  @Test
  public void deserializationTooManyBytes() {
    assertThatThrownBy(() -> Bitvector.fromBytes(Bytes.of(1, 2, 3), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Incorrect data size");
  }

  @Test
  void bitlistHashTest() {
    Bitlist bitlist = new Bitlist(2048, 2048);
    for (int i = 0; i < 44; i++) {
      bitlist.setBit(i);
    }
    Bytes32 hashOld =
        Bytes32.fromHexString("0x447ac4def72d4aa09ded8e1130cbe013511d4881c3393903ada630f034e985d7");

    ListViewRead<BitView> bitlistView = ViewUtils.createBitlistView(bitlist);
    Bytes32 hashNew = bitlistView.hashTreeRoot();

    Assertions.assertEquals(hashOld, hashNew);
  }
}
