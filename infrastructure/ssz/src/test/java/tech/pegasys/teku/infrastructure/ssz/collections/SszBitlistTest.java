/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.ssz.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszTestUtils;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszBitlistTest implements SszPrimitiveListTestBase {

  static Random random = new Random(1);
  static SszBitlistSchema<SszBitlist> emptySchema = SszBitlistSchema.create(0);
  static SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(500);
  static SszBitlistSchema<SszBitlist> hugeSchema = SszBitlistSchema.create(1L << 62);

  static SszBitlist random(SszBitlistSchema<?> schema, int size) {
    return schema.ofBits(
        size, IntStream.range(0, size).filter(__ -> random.nextBoolean()).toArray());
  }

  @Override
  public Stream<SszBitlist> sszData() {
    return Stream.of(
        emptySchema.empty(),
        schema.empty(),
        hugeSchema.empty(),
        random(schema, 1),
        random(hugeSchema, 1),
        random(schema, 2),
        random(hugeSchema, 2),
        random(schema, 254),
        schema.ofBits(254),
        schema.ofBits(254, IntStream.range(0, 254).toArray()),
        random(hugeSchema, 254),
        random(schema, 255),
        schema.ofBits(255),
        schema.ofBits(255, IntStream.range(0, 255).toArray()),
        random(hugeSchema, 255),
        random(schema, 256),
        schema.ofBits(256),
        schema.ofBits(256, IntStream.range(0, 256).toArray()),
        random(hugeSchema, 256),
        random(schema, 257),
        random(hugeSchema, 257),
        random(schema, 499),
        random(schema, 500),
        random(hugeSchema, 511),
        random(hugeSchema, 512),
        random(hugeSchema, 513),
        random(hugeSchema, 10000));
  }

  public Stream<Arguments> bitlistArgs() {
    return sszData().map(Arguments::of);
  }

  public Stream<Arguments> nonEmptyBitlistArgs() {
    return sszData().filter(b -> b.size() > 0).map(Arguments::of);
  }

  public Stream<Arguments> emptyBitlistArgs() {
    return sszData().filter(b -> b.size() == 0).map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testSszRoundtrip(SszBitlist bitlist1) {
    Bytes ssz1 = bitlist1.sszSerialize();
    SszBitlist bitlist2 = bitlist1.getSchema().sszDeserialize(ssz1);

    assertThat(bitlist2.getAllSetBits()).isEqualTo(bitlist1.getAllSetBits());
    assertThat(bitlist2.size()).isEqualTo(bitlist1.size());
    for (int i = 0; i < bitlist1.size(); i++) {
      assertThat(bitlist2.getBit(i)).isEqualTo(bitlist1.getBit(i));
      assertThat(bitlist2.get(i)).isEqualTo(bitlist1.get(i));
    }
    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());
    assertThat(bitlist2).isEqualTo(bitlist1);
    assertThat(bitlist2.hashCode()).isEqualTo(bitlist1.hashCode());

    Bytes ssz2 = bitlist2.sszSerialize();
    assertThat(ssz2).isEqualTo(ssz1);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testTreeRoundtrip(SszBitlist bitlist1) {
    TreeNode tree = bitlist1.getBackingNode();
    SszBitlist bitlist2 = bitlist1.getSchema().createFromBackingNode(tree);

    assertThat(bitlist2.getAllSetBits()).isEqualTo(bitlist1.getAllSetBits());
    assertThat(bitlist2.size()).isEqualTo(bitlist1.size());
    for (int i = 0; i < bitlist1.size(); i++) {
      assertThat(bitlist2.getBit(i)).isEqualTo(bitlist1.getBit(i));
      assertThat(bitlist2.get(i)).isEqualTo(bitlist1.get(i));
    }
    assertThat(bitlist2).isEqualTo(bitlist1);
    assertThat(bitlist2.hashCode()).isEqualTo(bitlist1.hashCode());
    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());
    assertThat(bitlist2.sszSerialize()).isEqualTo(bitlist1.sszSerialize());
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void or_testEqualList(SszBitlist bitlist) {
    SszBitlist res = bitlist.or(bitlist);
    assertThat(res).isEqualTo(bitlist);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void or_shouldThrowIfBitlistSizeIsLarger(SszBitlist bitlist) {
    SszBitlistSchema<SszBitlist> largerSchema =
        SszBitlistSchema.create(bitlist.getSchema().getMaxLength() + 1);
    SszBitlist largerBitlist = largerSchema.ofBits(bitlist.size() + 1, bitlist.size());
    assertThatThrownBy(() -> bitlist.or(largerBitlist))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void getBitCount_shouldReturnCorrectCount(SszBitlist bitlist) {
    long bitCount = bitlist.stream().filter(AbstractSszPrimitive::get).count();
    assertThat(bitlist.getBitCount()).isEqualTo(bitCount);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void intersects_shouldNotIntersectWithEmpty(SszBitlist bitlist) {
    assertThat(bitlist.intersects(bitlist.getSchema().empty())).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldIntersectWithSelf(SszBitlist bitlist) {
    if (bitlist.getBitCount() == 0) {
      return;
    }
    assertThat(bitlist.intersects(bitlist)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldNotIntersectWithZeroes(SszBitlist bitlist) {
    assertThat(bitlist.intersects(bitlist.getSchema().ofBits(bitlist.size()))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldNotIntersectWithNotSelf(SszBitlist bitlist) {
    assertThat(bitlist.intersects(SszTestUtils.not(bitlist))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldIntersectWithLargerBitlist(SszBitlist bitlist) {
    if (bitlist.getBitCount() == 0) {
      return;
    }
    SszBitlist largerBitlist =
        SszBitlistSchema.create(bitlist.size() + 1)
            .ofBits(
                bitlist.size() + 1,
                IntStream.concat(bitlist.streamAllSetBits(), IntStream.of(bitlist.size()))
                    .toArray());
    assertThat(bitlist.intersects(largerBitlist)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldIntersectWithFirstBit(SszBitlist bitlist) {
    OptionalInt maybeFirstBit = bitlist.streamAllSetBits().findFirst();
    if (maybeFirstBit.isEmpty()) {
      return;
    }
    int i = maybeFirstBit.orElseThrow();
    assertThat(bitlist.intersects(bitlist.getSchema().ofBits(i + 1, i))).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldIntersectWithLastBit(SszBitlist bitlist) {
    OptionalInt maybeLastBit = bitlist.streamAllSetBits().max();
    if (maybeLastBit.isEmpty()) {
      return;
    }
    int i = maybeLastBit.orElseThrow();
    assertThat(bitlist.intersects(bitlist.getSchema().ofBits(i + 1, i))).isTrue();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void isSupersetOf_shouldReturnTrueForSelf(SszBitlist bitlist) {
    assertThat(bitlist.isSuperSetOf(bitlist)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void isSupersetOf_shouldReturnTrueForEmpty(SszBitlist bitlist) {
    assertThat(bitlist.isSuperSetOf(bitlist.getSchema().empty())).isTrue();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void isSupersetOf_shouldReturnFalseForLarger(SszBitlist bitlist) {
    SszBitlist largerBitlist =
        SszBitlistSchema.create(bitlist.size() + 1).ofBits(bitlist.size() + 1, bitlist.size());
    assertThat(bitlist.isSuperSetOf(largerBitlist)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void isSupersetOf_shouldReturnFalseForNotSelf(SszBitlist bitlist) {
    if (bitlist.getBitCount() == bitlist.size()) {
      return;
    }
    assertThat(bitlist.isSuperSetOf(SszTestUtils.not(bitlist))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testOr(SszBitlist bitlist) {
    IntStream.of(1, 2, bitlist.size() - 1, bitlist.size())
        .filter(i -> i >= 0 && i < bitlist.size())
        .distinct()
        .forEach(
            orSize -> {
              SszBitlist orList = random(bitlist.getSchema(), orSize);
              SszBitlist res = bitlist.or(orList);
              assertThat(res.size()).isEqualTo(bitlist.size());
              assertThat(res.getSchema()).isEqualTo(bitlist.getSchema());
              for (int i = 0; i < bitlist.size(); i++) {
                if (i < orSize) {
                  assertThat(res.getBit(i)).isEqualTo(bitlist.getBit(i) || orList.getBit(i));
                } else {
                  assertThat(res.getBit(i)).isEqualTo(bitlist.getBit(i));
                }
              }
            });
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testOrWithEmptyBitlist(SszBitlist bitlist) {
    SszBitlist empty = bitlist.getSchema().empty();
    assertThat(bitlist.or(empty)).isEqualTo(bitlist);
  }

  @Test
  void testEmptyHashTreeRoot() {
    assertThat(emptySchema.empty().hashTreeRoot())
        .isEqualTo(Hash.sha256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
    assertThat(schema.empty().hashTreeRoot())
        .isEqualTo(
            Hash.sha256(Bytes.concatenate(TreeUtil.ZERO_TREES[1].hashTreeRoot(), Bytes32.ZERO)));
    assertThat(hugeSchema.empty().hashTreeRoot())
        .isEqualTo(
            Hash.sha256(
                Bytes.concatenate(TreeUtil.ZERO_TREES[62 - 8].hashTreeRoot(), Bytes32.ZERO)));
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void nullableOr_test(SszBitlist bitlist) {
    assertThatThrownBy(() -> SszBitlist.nullableOr(null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(SszBitlist.nullableOr(bitlist, null)).isEqualTo(bitlist);
    assertThat(SszBitlist.nullableOr(null, bitlist)).isEqualTo(bitlist);
    assertThat(SszBitlist.nullableOr(bitlist, SszTestUtils.not(bitlist)).getBitCount())
        .isEqualTo(bitlist.size());
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void createWritableCopy_shouldThrow(SszBitlist bitlist) {
    assertThatThrownBy(bitlist::createWritableCopy)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void basicTest() {
    for (int size :
        new int[] {
          /*100, 255, 256, */
          300, 1000, 1023
        }) {
      int[] bitIndexes =
          IntStream.concat(IntStream.range(0, size).filter(i -> i % 2 == 0), IntStream.of(0))
              .toArray();

      SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(size);
      SszList<SszBit> bitlist = schema.ofBits(size, bitIndexes);
      SszBitlist bitlist1 = schema.sszDeserialize(bitlist.sszSerialize());

      assertThat(bitlist1).isEqualTo(bitlist);
    }
  }

  @Disabled("the Tuweni Bytes issue: https://github.com/apache/incubator-tuweni/issues/186")
  @Test
  public void tuweniBytesIssue() {
    Bytes slicedBytes = Bytes.wrap(Bytes.wrap(new byte[32]), Bytes.wrap(new byte[6])).slice(0, 37);

    Assertions.assertThatCode(slicedBytes::copy).doesNotThrowAnyException();

    Bytes wrappedBytes = Bytes.wrap(slicedBytes, Bytes.wrap(new byte[1]));

    Assertions.assertThatCode(wrappedBytes::toArrayUnsafe).doesNotThrowAnyException();
    Assertions.assertThatCode(wrappedBytes::toArray).doesNotThrowAnyException();
    Assertions.assertThatCode(() -> Bytes.concatenate(slicedBytes, Bytes.wrap(new byte[1])))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @MethodSource("emptyBitlistArgs")
  void testBitEmptyListSsz(SszBitlist bitlist) {

    assertThat(bitlist.sszSerialize()).isEqualTo(Bytes.of(1));

    SszBitlist emptyList1 = bitlist.getSchema().sszDeserialize(Bytes.of(1));
    assertThat(emptyList1).isEmpty();
  }
}
