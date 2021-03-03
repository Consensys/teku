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

package tech.pegasys.teku.ssz.backing.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ssz.backing.SszDataAssert.assertThatSszData;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;

public class SszBitvectorTest {

  private static Random random = new Random(1);

  private static SszBitvector random(SszBitvectorSchema<?> schema) {
    return schema.ofBits(
        IntStream.range(0, schema.getLength()).filter(__ -> random.nextBoolean()).toArray());
  }

  public static Stream<Arguments> bitvectorArgs() {
    return Stream.of(
        Arguments.of(random(SszBitvectorSchema.create(1))),
        Arguments.of(random(SszBitvectorSchema.create(2))),
        Arguments.of(random(SszBitvectorSchema.create(7))),
        Arguments.of(random(SszBitvectorSchema.create(8))),
        Arguments.of(random(SszBitvectorSchema.create(9))),
        Arguments.of(random(SszBitvectorSchema.create(15))),
        Arguments.of(random(SszBitvectorSchema.create(16))),
        Arguments.of(random(SszBitvectorSchema.create(17))),
        Arguments.of(random(SszBitvectorSchema.create(255))),
        Arguments.of(random(SszBitvectorSchema.create(256))),
        Arguments.of(random(SszBitvectorSchema.create(257))),
        Arguments.of(random(SszBitvectorSchema.create(511))),
        Arguments.of(random(SszBitvectorSchema.create(512))),
        Arguments.of(random(SszBitvectorSchema.create(513))),
        Arguments.of(random(SszBitvectorSchema.create(1023))),
        Arguments.of(random(SszBitvectorSchema.create(1024))),
        Arguments.of(random(SszBitvectorSchema.create(1025))));
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testSszRoundtrip(SszBitvector bitvector1) {
    Bytes ssz1 = bitvector1.sszSerialize();
    SszBitvector bitvector2 = bitvector1.getSchema().sszDeserialize(ssz1);

    assertThat(bitvector2.getAllSetBits()).isEqualTo(bitvector1.getAllSetBits());
    assertThat(bitvector2.size()).isEqualTo(bitvector1.size());
    for (int i = 0; i < bitvector1.size(); i++) {
      assertThat(bitvector2.getBit(i)).isEqualTo(bitvector1.getBit(i));
      assertThat(bitvector2.get(i)).isEqualTo(bitvector1.get(i));
    }
    assertThatSszData(bitvector2).isEqualByAllMeansTo(bitvector1);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testTreeRoundtrip(SszBitvector bitvector1) {
    TreeNode tree = bitvector1.getBackingNode();
    SszBitvector bitvector2 = bitvector1.getSchema().createFromBackingNode(tree);

    assertThat(bitvector2.getAllSetBits()).isEqualTo(bitvector1.getAllSetBits());
    assertThat(bitvector2.size()).isEqualTo(bitvector1.size());
    for (int i = 0; i < bitvector1.size(); i++) {
      assertThat(bitvector2.getBit(i)).isEqualTo(bitvector1.getBit(i));
      assertThat(bitvector2.get(i)).isEqualTo(bitvector1.get(i));
    }
    assertThatSszData(bitvector2).isEqualByAllMeansTo(bitvector1);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void getBitCount_shouldReturnCorrectCount(SszBitvector bitvector) {
    long bitCount = bitvector.stream().filter(AbstractSszPrimitive::get).count();
    assertThat(bitvector.getBitCount()).isEqualTo(bitCount);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void createWritableCopy_shouldThrow(SszBitvector bitvector) {
    assertThatThrownBy(bitvector::createWritableCopy)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void rightShift_shouldYieldAllZeroesWhenShiftingByVectorLength(SszBitvector bitvector) {
    assertThat(bitvector.rightShift(bitvector.size()).getBitCount()).isZero();
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void rightShift_zeroShiftShouldYieldTheSameVector(SszBitvector bitvector) {
    assertThatSszData(bitvector.rightShift(0)).isEqualByAllMeansTo(bitvector);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void rightShift_test(SszBitvector vector) {
    SszBitvectorSchema<?> schema = vector.getSchema();
    IntStream.of(
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            15,
            16,
            17,
            31,
            32,
            33,
            255,
            256,
            257,
            511,
            512,
            513,
            schema.getLength() - 1,
            schema.getLength(),
            schema.getLength() + 1)
        .forEach(
            i -> {
              SszBitvector shiftedVector = vector.rightShift(i);
              SszBitvector vectorExpected =
                  schema.ofBits(
                      vector
                          .streamAllSetBits()
                          .map(b -> b + i)
                          .filter(b -> b < schema.getLength())
                          .toArray());
              assertThatSszData(shiftedVector).isEqualByAllMeansTo(vectorExpected);
            });
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testBitMethodsAreConsistent(SszBitvector vector) {
    assertThat(vector.streamAllSetBits())
        .containsExactlyInAnyOrderElementsOf(vector.getAllSetBits());
    List<Integer> bitsIndexes = vector.getAllSetBits();
    for (int i = 0; i < vector.size(); i++) {
      assertThat(vector.getBit(i)).isEqualTo(bitsIndexes.contains(i));
    }
    assertThat(vector.getBitCount()).isEqualTo(bitsIndexes.size());
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void get_shouldThrowIndexOutOfBounds(SszBitvector vector) {
    assertThatThrownBy(() -> vector.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
  }
}
