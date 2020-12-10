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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.MutableBitlist;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.view.BasicViews;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

public class BitlistTest {
  protected static final int BITLIST_MAX_SIZE = 4000;

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void initTest(final String name, final BitlistFactory factory) {
    Bitlist bitlist = factory.createDefaultBitlist();
    assertFalse(bitlist.getBit(0));
    assertFalse(bitlist.getBit(9));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void setTest(final String name, final BitlistFactory factory) {
    Bitlist bitlist = factory.createWithBits(1, 3, 8);

    assertFalse(bitlist.getBit(0));
    assertTrue(bitlist.getBit(1));
    assertTrue(bitlist.getBit(3));
    assertFalse(bitlist.getBit(4));
    assertTrue(bitlist.getBit(8));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void getAllSetBits(final String name, final BitlistFactory factory) {
    Bitlist bitlist = factory.createWithBits(0, 1, 3, 8, 9);

    assertThat(bitlist.getAllSetBits()).containsExactly(0, 1, 3, 8, 9);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void getAllSetBits_noSetBits(final String name, final BitlistFactory factory) {
    Bitlist bitlist = factory.createWithBits();

    assertThat(bitlist.getAllSetBits()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void intersects_noOverlap(final String name, final BitlistFactory factory) {
    Bitlist bitlist1 = factory.createWithBits(1, 3, 5);
    Bitlist bitlist2 = factory.createWithBits(0, 2, 4);

    assertThat(bitlist1.intersects(bitlist2)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void intersects_withOverlap(final String name, final BitlistFactory factory) {
    Bitlist bitlist1 = factory.createWithBits(1, 3, 5);
    Bitlist bitlist2 = factory.createWithBits(0, 3, 4);

    assertThat(bitlist1.intersects(bitlist2)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void isSuperSetOf_sameBitsSet(final String name, final BitlistFactory factory) {
    Bitlist bitlist1 = factory.createWithBits(1, 3, 5);
    Bitlist bitlist2 = factory.createWithBits(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void isSuperSetOf_additionalBitsSet(final String name, final BitlistFactory factory) {
    Bitlist bitlist1 = factory.createWithBits(1, 3, 5, 7, 9);
    Bitlist bitlist2 = factory.createWithBits(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void isSuperSetOf_notAllBitsSet(final String name, final BitlistFactory factory) {
    Bitlist bitlist1 = factory.createWithBits(1, 3);
    Bitlist bitlist2 = factory.createWithBits(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void isSuperSetOf_differentBitsSet(final String name, final BitlistFactory factory) {
    Bitlist bitlist1 = factory.createWithBits(2, 5, 6);
    Bitlist bitlist2 = factory.createWithBits(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void countSetBits(final String name, final BitlistFactory factory) {
    assertThat(factory.createWithBits(1, 2, 6, 7, 9).getBitCount()).isEqualTo(5);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void serializationTest(final String name, final BitlistFactory factory) {
    Bitlist bitlist = factory.createDefaultBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Assertions.assertEquals(bitlistSerialized.toHexString(), "0x721806");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void deserializationTest(final String name, final BitlistFactory factory) {
    Bitlist bitlist = factory.createDefaultBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Bitlist newBitlist = Bitlist.fromBytes(bitlistSerialized, BITLIST_MAX_SIZE);
    assertThat(newBitlist).isEqualTo(bitlist);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void serializationTest2(final String name, final BitlistFactory factory) {
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void deserializationTest2(final String name, final BitlistFactory factory) {
    final Bitlist bitlist = factory.create(9, BITLIST_MAX_SIZE, 0, 3, 4, 5, 6, 7, 8);

    Bitlist newBitlist = Bitlist.fromBytes(Bytes.fromHexString("0xf903"), BITLIST_MAX_SIZE);
    assertThat(newBitlist).isEqualTo(bitlist);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void deserializationShouldRejectZeroLengthBytes(
      final String name, final BitlistFactory factory) {
    assertThatThrownBy(() -> Bitlist.fromBytes(Bytes.EMPTY, BITLIST_MAX_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one byte");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getBitlistFactoryArguments")
  public void deserializationShouldRejectDataWhenEndMarkerBitNotSet(
      final String name, final BitlistFactory factory) {
    assertThatThrownBy(() -> Bitlist.fromBytes(Bytes.of(0), BITLIST_MAX_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("marker bit");
  }

  @Test
  public void equals_sameBits() {
    final List<BitlistFactory> factories = getBitlistFactories();
    final List<Bitlist> bitlists =
        factories.stream().map(BitlistFactory::createDefaultBitlist).collect(Collectors.toList());

    for (int i = 0; i < bitlists.size(); i++) {
      for (int j = i; j < bitlists.size(); j++) {
        final Bitlist bitlistA = bitlists.get(i);
        final Bitlist bitlistB = bitlists.get(j);

        assertThat(bitlistA).isEqualTo(bitlistB);
        assertThat(bitlistB).isEqualTo(bitlistA);
      }
    }
  }

  @Test
  public void equals_differentBits() {
    final List<BitlistFactory> factories = getBitlistFactories();
    final List<Bitlist> bitlistsA =
        factories.stream().map(f -> f.createWithBits(1, 3, 5)).collect(Collectors.toList());
    final List<Bitlist> bitlistsB =
        factories.stream().map(f -> f.createWithBits(1, 2)).collect(Collectors.toList());

    for (int i = 0; i < bitlistsA.size(); i++) {
      for (int j = 0; j < bitlistsA.size(); j++) {
        final Bitlist bitlistA = bitlistsA.get(i);
        final Bitlist bitlistB = bitlistsB.get(j);

        assertThat(bitlistA).isNotEqualTo(bitlistB);
        assertThat(bitlistB).isNotEqualTo(bitlistA);
      }
    }
  }

  @Test
  public void equals_differentMaxSize() {
    final List<BitlistFactory> factories = getBitlistFactories();
    final List<Bitlist> bitlistsA =
        factories.stream().map(f -> f.create(5, 10, 1, 2)).collect(Collectors.toList());
    final List<Bitlist> bitlistsB =
        factories.stream().map(f -> f.create(5, 5, 1, 2)).collect(Collectors.toList());

    for (int i = 0; i < bitlistsA.size(); i++) {
      for (int j = 0; j < bitlistsA.size(); j++) {
        final Bitlist bitlistA = bitlistsA.get(i);
        final Bitlist bitlistB = bitlistsB.get(j);

        assertThat(bitlistA).isNotEqualTo(bitlistB);
        assertThat(bitlistB).isNotEqualTo(bitlistA);
      }
    }
  }

  @Test
  public void equals_differentSize() {
    final List<BitlistFactory> factories = getBitlistFactories();
    final List<Bitlist> bitlistsA =
        factories.stream().map(f -> f.create(5, 10, 1, 2)).collect(Collectors.toList());
    final List<Bitlist> bitlistsB =
        factories.stream().map(f -> f.create(6, 10, 1, 2)).collect(Collectors.toList());

    for (int i = 0; i < bitlistsA.size(); i++) {
      for (int j = 0; j < bitlistsA.size(); j++) {
        final Bitlist bitlistA = bitlistsA.get(i);
        final Bitlist bitlistB = bitlistsB.get(j);

        assertThat(bitlistA).isNotEqualTo(bitlistB);
        assertThat(bitlistB).isNotEqualTo(bitlistA);
      }
    }
  }

  @Test
  public void hashCode_sameBits() {
    final List<BitlistFactory> factories = getBitlistFactories();
    final List<Bitlist> bitlists =
        factories.stream().map(BitlistFactory::createDefaultBitlist).collect(Collectors.toList());

    for (int i = 0; i < bitlists.size(); i++) {
      for (int j = i; j < bitlists.size(); j++) {
        final Bitlist bitlistA = bitlists.get(i);
        final Bitlist bitlistB = bitlists.get(j);

        assertThat(bitlistA.hashCode()).isEqualTo(bitlistB.hashCode());
      }
    }
  }

  @Test
  public void hashCode_differentBits() {
    final List<BitlistFactory> factories = getBitlistFactories();
    final List<Bitlist> bitlistsA =
        factories.stream().map(f -> f.createWithBits(1, 3, 5)).collect(Collectors.toList());
    final List<Bitlist> bitlistsB =
        factories.stream().map(f -> f.createWithBits(1, 2)).collect(Collectors.toList());

    for (int i = 0; i < bitlistsA.size(); i++) {
      for (int j = 0; j < bitlistsA.size(); j++) {
        final Bitlist bitlistA = bitlistsA.get(i);
        final Bitlist bitlistB = bitlistsB.get(j);

        assertThat(bitlistA.hashCode()).isNotEqualTo(bitlistB.hashCode());
      }
    }
  }

  @Test
  public void hashCode_differentSize() {
    final List<BitlistFactory> factories = getBitlistFactories();
    final List<Bitlist> bitlistsA =
        factories.stream().map(f -> f.create(5, 10, 1, 2)).collect(Collectors.toList());
    final List<Bitlist> bitlistsB =
        factories.stream().map(f -> f.create(6, 10, 1, 2)).collect(Collectors.toList());

    for (int i = 0; i < bitlistsA.size(); i++) {
      for (int j = 0; j < bitlistsA.size(); j++) {
        final Bitlist bitlistA = bitlistsA.get(i);
        final Bitlist bitlistB = bitlistsB.get(j);

        assertThat(bitlistA.hashCode()).isNotEqualTo(bitlistB.hashCode());
      }
    }
  }

  @Test
  public void hashCode_differentMaxSize() {
    final List<BitlistFactory> factories = getBitlistFactories();
    final List<Bitlist> bitlistsA =
        factories.stream().map(f -> f.create(5, 10, 1, 2)).collect(Collectors.toList());
    final List<Bitlist> bitlistsB =
        factories.stream().map(f -> f.create(5, 5, 1, 2)).collect(Collectors.toList());

    for (int i = 0; i < bitlistsA.size(); i++) {
      for (int j = 0; j < bitlistsA.size(); j++) {
        final Bitlist bitlistA = bitlistsA.get(i);
        final Bitlist bitlistB = bitlistsB.get(j);

        assertThat(bitlistA.hashCode()).isNotEqualTo(bitlistB.hashCode());
      }
    }
  }

  public static Stream<Arguments> getBitlistFactoryArguments() {
    final BitlistFactory defaultBitlistFactory = BitlistTest::createDefaultBitlist;
    final BitlistFactory viewBackedBitlistFactory = BitlistTest::createViewBackedBitlist;
    return Stream.of(
        Arguments.of("DefaultBitlist", defaultBitlistFactory),
        Arguments.of("ViewBackedBitlist", viewBackedBitlistFactory));
  }

  public static List<BitlistFactory> getBitlistFactories() {
    return getBitlistFactoryArguments()
        .map(args -> args.get()[1])
        .map(BitlistFactory.class::cast)
        .collect(Collectors.toList());
  }

  private static Bitlist createDefaultBitlist(
      final int size, final int maxSize, final int... bits) {
    MutableBitlist bitlist = MutableBitlist.create(size, maxSize);
    IntStream.of(bits).forEach(bitlist::setBit);
    return bitlist;
  }

  private static Bitlist createViewBackedBitlist(
      final int size, final int maxSize, final int... bits) {
    MutableBitlist bitlist = MutableBitlist.create(size, maxSize);
    IntStream.of(bits).forEach(bitlist::setBit);

    final ListViewRead<BasicViews.BitView> view = ViewUtils.createBitlistView(bitlist);
    return ViewUtils.getBitlist(view);
  }

  @FunctionalInterface
  private interface BitlistFactory {
    Bitlist create(final int size, final int maxSize, final int... bits);

    default Bitlist createDefaultBitlist() {
      return createWithBits(1, 4, 5, 6, 11, 12, 17);
    }

    default Bitlist createWithBits(int... bits) {
      return create(18, BITLIST_MAX_SIZE, bits);
    }
  }
}
