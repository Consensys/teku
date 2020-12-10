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

package tech.pegasys.teku.ssz.ssztypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.MutableBitlist;

public abstract class AbstractBitlistTest {
  protected static final int BITLIST_MAX_SIZE = 4000;

  @Test
  void initTest() {
    Bitlist bitlist = createDefaultBitlist();
    assertFalse(bitlist.getBit(0));
    assertFalse(bitlist.getBit(9));
  }

  @Test
  void setTest() {
    Bitlist bitlist = createBitlist(1, 3, 8);

    assertFalse(bitlist.getBit(0));
    assertTrue(bitlist.getBit(1));
    assertTrue(bitlist.getBit(3));
    assertFalse(bitlist.getBit(4));
    assertTrue(bitlist.getBit(8));
  }

  @Test
  void getAllSetBits() {
    Bitlist bitlist = createBitlist(0, 1, 3, 8, 9);

    assertThat(bitlist.getAllSetBits()).containsExactly(0, 1, 3, 8, 9);
  }

  @Test
  void getAllSetBits_noSetBits() {
    Bitlist bitlist = createBitlist();

    assertThat(bitlist.getAllSetBits()).isEmpty();
  }

  @Test
  void intersects_noOverlap() {
    Bitlist bitlist1 = createBitlist(1, 3, 5);
    Bitlist bitlist2 = createBitlist(0, 2, 4);

    assertThat(bitlist1.intersects(bitlist2)).isFalse();
  }

  @Test
  void intersects_withOverlap() {
    Bitlist bitlist1 = createBitlist(1, 3, 5);
    Bitlist bitlist2 = createBitlist(0, 3, 4);

    assertThat(bitlist1.intersects(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_sameBitsSet() {
    Bitlist bitlist1 = createBitlist(1, 3, 5);
    Bitlist bitlist2 = createBitlist(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_additionalBitsSet() {
    Bitlist bitlist1 = createBitlist(1, 3, 5, 7, 9);
    Bitlist bitlist2 = createBitlist(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_notAllBitsSet() {
    Bitlist bitlist1 = createBitlist(1, 3);
    Bitlist bitlist2 = createBitlist(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isFalse();
  }

  @Test
  void isSuperSetOf_differentBitsSet() {
    Bitlist bitlist1 = createBitlist(2, 5, 6);
    Bitlist bitlist2 = createBitlist(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isFalse();
  }

  @Test
  void countSetBits() {
    assertThat(createBitlist(1, 2, 6, 7, 9).getBitCount()).isEqualTo(5);
  }

  @Test
  void serializationTest() {
    Bitlist bitlist = createDefaultBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Assertions.assertEquals(bitlistSerialized.toHexString(), "0x721806");
  }

  @Test
  void deserializationTest() {
    Bitlist bitlist = createDefaultBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Bitlist newBitlist = Bitlist.fromBytes(bitlistSerialized, BITLIST_MAX_SIZE);
    assertThat(Bitlist.equals(bitlist, newBitlist)).isTrue();
  }

  @Test
  void serializationTest2() {
    MutableBitlist bitlist = MutableBitlist.create(9, BITLIST_MAX_SIZE);
    bitlist.setBit(0);
    bitlist.setBit(3);
    bitlist.setBit(4);
    bitlist.setBit(5);
    bitlist.setBit(6);
    bitlist.setBit(7);
    bitlist.setBit(8);

    Bytes bitlistSerialized = bitlist.serialize();
    Assertions.assertEquals(Bytes.fromHexString("0xf903"), bitlistSerialized);
  }

  @Test
  void deserializationTest2() {
    final Bitlist bitlist = createBitlistWithSize(9, BITLIST_MAX_SIZE, 0, 3, 4, 5, 6, 7, 8);

    Bitlist newBitlist = Bitlist.fromBytes(Bytes.fromHexString("0xf903"), BITLIST_MAX_SIZE);
    assertThat(Bitlist.equals(bitlist, newBitlist)).isTrue();
  }

  @Test
  void deserializationShouldRejectZeroLengthBytes() {
    assertThatThrownBy(() -> Bitlist.fromBytes(Bytes.EMPTY, BITLIST_MAX_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one byte");
  }

  @Test
  void deserializationShouldRejectDataWhenEndMarkerBitNotSet() {
    assertThatThrownBy(() -> Bitlist.fromBytes(Bytes.of(0), BITLIST_MAX_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("marker bit");
  }

  protected Bitlist createDefaultBitlist() {
    return createBitlist(1, 4, 5, 6, 11, 12, 17);
  }

  protected Bitlist createBitlist(int... bits) {
    return createBitlistWithSize(18, BITLIST_MAX_SIZE, bits);
  }

  protected abstract Bitlist createBitlistWithSize(int size, final int maxSize, int... bits);
}
