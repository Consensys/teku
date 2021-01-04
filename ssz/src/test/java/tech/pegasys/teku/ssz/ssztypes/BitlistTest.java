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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;

class BitlistTest {
  private static final int BITLIST_MAX_SIZE = 4000;

  @Test
  void initTest() {
    Bitlist bitlist = create();
    assertFalse(bitlist.getBit(0));
    assertFalse(bitlist.getBit(9));
  }

  @Test
  void setTest() {
    Bitlist bitlist = create(1, 3, 8);

    assertFalse(bitlist.getBit(0));
    assertTrue(bitlist.getBit(1));
    assertTrue(bitlist.getBit(3));
    assertFalse(bitlist.getBit(4));
    assertTrue(bitlist.getBit(8));
  }

  @Test
  void getAllSetBits() {
    Bitlist bitlist = create(0, 1, 3, 8, 9);

    assertThat(bitlist.getAllSetBits()).containsExactly(0, 1, 3, 8, 9);
  }

  @Test
  void getAllSetBits_noSetBits() {
    Bitlist bitlist = create();

    assertThat(bitlist.getAllSetBits()).isEmpty();
  }

  @Test
  void intersects_noOverlap() {
    Bitlist bitlist1 = create(1, 3, 5);
    Bitlist bitlist2 = create(0, 2, 4);

    assertThat(bitlist1.intersects(bitlist2)).isFalse();
  }

  @Test
  void intersects_withOverlap() {
    Bitlist bitlist1 = create(1, 3, 5);
    Bitlist bitlist2 = create(0, 3, 4);

    assertThat(bitlist1.intersects(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_sameBitsSet() {
    Bitlist bitlist1 = create(1, 3, 5);
    Bitlist bitlist2 = create(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_additionalBitsSet() {
    Bitlist bitlist1 = create(1, 3, 5, 7, 9);
    Bitlist bitlist2 = create(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_notAllBitsSet() {
    Bitlist bitlist1 = create(1, 3);
    Bitlist bitlist2 = create(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isFalse();
  }

  @Test
  void isSuperSetOf_differentBitsSet() {
    Bitlist bitlist1 = create(2, 5, 6);
    Bitlist bitlist2 = create(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isFalse();
  }

  @Test
  void countSetBits() {
    assertThat(create(1, 2, 6, 7, 9).getBitCount()).isEqualTo(5);
  }

  @Test
  void serializationTest() {
    Bitlist bitlist = createBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Assertions.assertEquals(bitlistSerialized.toHexString(), "0x721806");
  }

  @Test
  void deserializationTest() {
    Bitlist bitlist = createBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Bitlist newBitlist = Bitlist.fromSszBytes(bitlistSerialized, BITLIST_MAX_SIZE);
    Assertions.assertEquals(bitlist, newBitlist);
  }

  @Test
  void serializationTest2() {
    Bitlist bitlist = new Bitlist(9, BITLIST_MAX_SIZE);
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
    Bitlist bitlist = new Bitlist(9, BITLIST_MAX_SIZE);
    bitlist.setBit(0);
    bitlist.setBit(3);
    bitlist.setBit(4);
    bitlist.setBit(5);
    bitlist.setBit(6);
    bitlist.setBit(7);
    bitlist.setBit(8);

    Bitlist newBitlist = Bitlist.fromSszBytes(Bytes.fromHexString("0xf903"), BITLIST_MAX_SIZE);
    Assertions.assertEquals(bitlist, newBitlist);
  }

  @Test
  void deserializationShouldRejectZeroLengthBytes() {
    assertThatThrownBy(() -> Bitlist.fromSszBytes(Bytes.EMPTY, BITLIST_MAX_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one byte");
  }

  @Test
  void deserializationShouldRejectDataWhenEndMarkerBitNotSet() {
    assertThatThrownBy(() -> Bitlist.fromSszBytes(Bytes.of(0), BITLIST_MAX_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("marker bit");
  }

  private static Bitlist create(int... bits) {
    Bitlist bitlist = new Bitlist(18, BITLIST_MAX_SIZE);
    IntStream.of(bits).forEach(bitlist::setBit);
    return bitlist;
  }

  private static Bitlist createBitlist() {
    return create(1, 4, 5, 6, 11, 12, 17);
  }

  static Stream<Arguments> sszBitlistCases() {
    return Stream.of(
        Arguments.of(Bytes.of(0b1)),
        Arguments.of(Bytes.of(0b11)),
        Arguments.of(Bytes.of(0b10)),
        Arguments.of(Bytes.of(0b101)),
        Arguments.of(Bytes.of(0b110)),
        Arguments.of(Bytes.of(0b111)),
        Arguments.of(Bytes.of(0b100)),
        Arguments.of(Bytes.of(0b1111)),
        Arguments.of(Bytes.of(0b11111)),
        Arguments.of(Bytes.of(0b111111)),
        Arguments.of(Bytes.of(0b1111111)),
        Arguments.of(Bytes.of(0b11111111)),
        Arguments.of(Bytes.of(0b10111111)),
        Arguments.of(Bytes.of(0b10111111, 0b1)),
        Arguments.of(Bytes.of(0b00111111, 0b1)),
        Arguments.of(Bytes.of(0b10111111, 0b11)),
        Arguments.of(Bytes.of(0b10111111, 0b10)),
        Arguments.of(Bytes.of(0b10111111, 0b1111111)),
        Arguments.of(Bytes.of(0b10111111, 0b11111111)),
        Arguments.of(Bytes.of(0b10111111, 0b11111111, 0b1)));
  }

  @ParameterizedTest
  @MethodSource("sszBitlistCases")
  void testSszMethods(Bytes bitlistSsz) {
    int length = Bitlist.sszGetLengthAndValidate(bitlistSsz);
    Bytes truncBytes = Bitlist.sszTruncateLeadingBit(bitlistSsz, length);
    Bytes bitlistSsz1 = Bitlist.sszAppendLeadingBit(truncBytes, length);
    assertThat(bitlistSsz1).isEqualTo(bitlistSsz);

    Bitlist bitlist = Bitlist.fromSszBytes(bitlistSsz, length);
    Bytes bitlistSsz2 = bitlist.serialize();
    assertThat(bitlistSsz2).isEqualTo(bitlistSsz);
  }

  static Stream<Arguments> sszInvalidBitlistCases() {
    return Stream.of(
        Arguments.of(Bytes.of(0b0)),
        Arguments.of(Bytes.EMPTY),
        Arguments.of(Bytes.of(0b10101010, 0b0)));
  }

  @ParameterizedTest
  @MethodSource("sszInvalidBitlistCases")
  void testSszMethodsInvalid(Bytes bitlistSsz) {
    assertThatThrownBy(() -> Bitlist.sszGetLengthAndValidate(bitlistSsz))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Bitlist.fromSszBytes(bitlistSsz, 1024))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
