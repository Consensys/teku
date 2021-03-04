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

package tech.pegasys.teku.ssz.backing;

import static java.lang.Integer.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ssz.backing.SszDataAssert.assertThatSszData;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSubContainer;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

public class SszListTest implements SszListAbstractTest, SszMutableCompositeAbstractTest {

  private final RandomSszDataGenerator randomSsz =
      new RandomSszDataGenerator().withMaxListSize(256);

  @Override
  public Stream<SszList<?>> sszData() {
    return SszCollectionAbstractTest.complexElementSchemas()
        .flatMap(
            elementSchema ->
                Stream.of(
                    SszListSchema.create(elementSchema, 0),
                    SszListSchema.create(elementSchema, 1),
                    SszListSchema.create(elementSchema, 2),
                    SszListSchema.create(elementSchema, 3),
                    SszListSchema.create(elementSchema, 10),
                    SszListSchema.create(elementSchema, 1L << 33)))
        .flatMap(
            vectorSchema ->
                Stream.of(vectorSchema.getDefault(), randomSsz.randomData(vectorSchema)));
  }

  @Test
  void testMutableListReusable() {
    List<TestSubContainer> elements =
        IntStream.range(0, 5)
            .mapToObj(i -> new TestSubContainer(UInt64.valueOf(i), Bytes32.leftPad(Bytes.of(i))))
            .collect(Collectors.toList());

    SszListSchema<TestSubContainer, ?> type =
        SszListSchema.create(TestContainers.TestSubContainer.SSZ_SCHEMA, 100);
    SszList<TestSubContainer> lr1 = type.getDefault();
    SszMutableList<TestSubContainer> lw1 = lr1.createWritableCopy();

    assertThat(lw1.sszSerialize()).isEqualTo(lr1.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr1.hashTreeRoot());

    lw1.append(elements.get(0));
    SszMutableList<TestSubContainer> lw2 = type.getDefault().createWritableCopy();
    lw2.append(elements.get(0));
    SszList<TestSubContainer> lr2 = lw2.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr2.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    lw1.appendAll(elements.subList(1, 5));
    SszMutableList<TestSubContainer> lw3 = type.getDefault().createWritableCopy();
    lw3.appendAll(elements);
    SszList<TestSubContainer> lr3 = lw3.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr3.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr3.hashTreeRoot());

    lw1.clear();

    assertThat(lw1.sszSerialize()).isEqualTo(lr1.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr1.hashTreeRoot());

    lw1.appendAll(elements.subList(0, 5));
    SszMutableList<TestSubContainer> lw4 = type.getDefault().createWritableCopy();
    lw4.appendAll(elements);
    SszList<TestSubContainer> lr4 = lw3.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr4.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr4.hashTreeRoot());

    lw1.clear();
    lw1.append(elements.get(0));

    assertThat(lw1.sszSerialize()).isEqualTo(lr2.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());
  }

  @Disabled // TODO either remove or generalize
  @ParameterizedTest
  @MethodSource("testAllNonBitListTypeParameters")
  <T extends SszData> void clearTest1(SszSchema<T> listElementType, long maxLength) {
    if (maxLength == 0) {
      return;
    }

    SszListSchema<T, ?> sszListSchema = SszListSchema.create(listElementType, maxLength);
    SszList<T> lr1 = sszListSchema.getDefault();
    SszMutableList<T> lw1 = lr1.createWritableCopy();
    lw1.append(randomSsz.randomData(listElementType));
    if (maxLength > 1) {
      lw1.append(randomSsz.randomData(listElementType));
    }
    SszMutableList<T> lw2 = lw1.commitChanges().createWritableCopy();
    lw2.clear();
    SszList<T> lr2 = lw2.commitChanges();
    assertThat(lr1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    SszMutableList<T> lw3 = lw1.commitChanges().createWritableCopy();
    lw3.clear();
    lw3.append(randomSsz.randomData(listElementType));
    SszList<T> lr3 = lw3.commitChanges();
    assertThat(lr3.size()).isEqualTo(1);
  }

  @Disabled // TODO either remove or generalize
  @ParameterizedTest
  @MethodSource("testAllListTypeParameters")
  <T extends SszData> void testListSszDeserializeFailsFastWithTooLongData(
      SszSchema<T> listElementType, long maxLengthL) {

    if (maxLengthL > 1024) {
      return;
    }
    int maxLength = (int) maxLengthL;

    SszListSchema<T, ?> sszListSchema = SszListSchema.create(listElementType, maxLength);
    SszListSchema<T, ?> largerSszListSchema = SszListSchema.create(listElementType, maxLength + 10);

    // should normally deserialize smaller lists
    for (int i = max(0, maxLength - 8); i <= maxLength; i++) {
      List<T> children =
          Stream.generate(listElementType::getDefault).limit(i).collect(Collectors.toList());
      SszList<T> largerList = largerSszListSchema.createFromElements(children);
      Bytes ssz = largerList.sszSerialize();
      SszList<T> resList = sszListSchema.sszDeserialize(ssz);

      assertThat(resList.size()).isEqualTo(i);
    }

    // should fail fast when ssz is longer than max
    for (int i = maxLength + 1; i < maxLength + 10; i++) {
      List<T> children =
          Stream.generate(listElementType::getDefault).limit(i).collect(Collectors.toList());
      SszList<T> largerList = largerSszListSchema.createFromElements(children);
      Bytes ssz = largerList.sszSerialize();

      SszReader sszReader = SszReader.fromBytes(ssz);
      assertThatThrownBy(() -> sszListSchema.sszDeserialize(sszReader))
          .isInstanceOf(SszDeserializeException.class);
      if (listElementType != SszPrimitiveSchemas.BIT_SCHEMA || i > maxLength + 8) {
        assertThat(sszReader.getAvailableBytes()).isGreaterThan(0);
      }
    }
  }

  @Disabled // TODO either remove or generalize
  @ParameterizedTest
  @MethodSource("testPrimitiveNonBitListTypeParameters")
  <T extends SszData> void testNonBitEmptyListSsz(SszSchema<T> listElementType, long maxLength) {

    SszListSchema<T, ?> sszListSchema = SszListSchema.create(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.sszSerialize()).isEqualTo(Bytes.EMPTY);

    SszList<T> emptyList1 = sszListSchema.sszDeserialize(Bytes.EMPTY);
    assertThat(emptyList1).isEmpty();
  }

  @Disabled // TODO either remove or generalize
  @ParameterizedTest
  @MethodSource("testPrimitiveBitListTypeParameters")
  <T extends SszData> void testBitEmptyListSsz(SszSchema<T> listElementType, long maxLength) {

    SszListSchema<T, ?> sszListSchema = SszListSchema.create(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.sszSerialize()).isEqualTo(Bytes.of(1));

    SszList<T> emptyList1 = sszListSchema.sszDeserialize(Bytes.of(1));
    assertThat(emptyList1).isEmpty();
  }

  @Disabled // TODO either remove or generalize
  @ParameterizedTest
  @MethodSource("testAllListTypeParameters")
  <T extends SszData> void testEmptyListHash(SszSchema<T> listElementType, long maxLength) {

    SszListSchema<T, ?> sszListSchema = SszListSchema.create(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.hashTreeRoot())
        .isEqualTo(
            Hash.sha2_256(
                Bytes.concatenate(
                    TreeUtil.ZERO_TREES[sszListSchema.treeDepth()].hashTreeRoot(), Bytes32.ZERO)));
  }

  @Disabled // TODO either remove or generalize
  @ParameterizedTest
  @MethodSource("testAllListTypeParameters")
  <T extends SszData> void testSszRoundtrip(SszSchema<T> listElementType, long maxLength) {

    SszListSchema<T, ?> sszListSchema = SszListSchema.create(listElementType, maxLength);

    LongStream.of(1, 2, 3, 4, 5, maxLength - 1, maxLength)
        .takeWhile(i -> i <= maxLength)
        .takeWhile(i -> i < 1024)
        .forEach(
            size -> {
              SszList<T> list =
                  sszListSchema.createFromElements(
                      randomSsz
                          .randomDataStream(listElementType)
                          .limit(size)
                          .collect(Collectors.toList()));
              Bytes ssz = list.sszSerialize();
              SszList<T> list1 = sszListSchema.sszDeserialize(ssz);
              assertThatSszData(list1).isEqualByAllMeansTo(list);
            });
  }
}
