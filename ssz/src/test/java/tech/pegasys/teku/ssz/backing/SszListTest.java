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

import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.backing.TestContainers.TestDoubleSuperContainer;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSubContainer;
import tech.pegasys.teku.ssz.backing.TestContainers.VariableSizeContainer;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes4;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

public class SszListTest {

  private static final Random random = new Random(1);
  private static final Supplier<SszBit> bitSupplier = () -> SszBit.viewOf(random.nextBoolean());
  private static final Supplier<SszByte> byteSupplier = () -> new SszByte((byte) random.nextInt());
  private static final Supplier<SszBytes4> bytes4Supplier =
      () -> new SszBytes4(Bytes4.rightPad(Bytes.random(4, random)));
  private static final Supplier<SszUInt64> uintSupplier =
      () -> SszUInt64.fromLong(random.nextLong());
  private static final Supplier<SszBytes32> bytes32Supplier =
      () -> new SszBytes32(Bytes32.random(random));

  @Test
  void testMutableListReusable() {
    List<TestSubContainer> elements =
        IntStream.range(0, 5)
            .mapToObj(i -> new TestSubContainer(UInt64.valueOf(i), Bytes32.leftPad(Bytes.of(i))))
            .collect(Collectors.toList());

    SszListSchema<TestSubContainer> type =
        new SszListSchema<>(TestContainers.TestSubContainer.SSZ_SCHEMA, 100);
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

  static <T extends SszData> T randomData(SszSchema<T> schema) {
    return randomDataStream(schema).findFirst().orElseThrow();
  }

  @SuppressWarnings("unchecked")
  static <T extends SszData> Stream<T> randomDataStream(SszSchema<T> schema) {
    if (schema instanceof SszPrimitiveSchema) {
      if (schema == SszPrimitiveSchemas.BIT_SCHEMA) {
        return (Stream<T>) Stream.generate(bitSupplier);
      } else if (schema == SszPrimitiveSchemas.BYTE_SCHEMA) {
        return (Stream<T>) Stream.generate(byteSupplier);
      } else if (schema == SszPrimitiveSchemas.UINT64_SCHEMA) {
        return (Stream<T>) Stream.generate(uintSupplier);
      } else if (schema == SszPrimitiveSchemas.BYTES4_SCHEMA) {
        return (Stream<T>) Stream.generate(bytes4Supplier);
      } else if (schema == SszPrimitiveSchemas.BYTES32_SCHEMA) {
        return (Stream<T>) Stream.generate(bytes32Supplier);
      } else {
        throw new IllegalArgumentException("Unknown primitive schema: " + schema);
      }
    } else if (schema instanceof SszContainerSchema) {
      SszContainerSchema<SszContainer> containerSchema = (SszContainerSchema<SszContainer>) schema;
      return Stream.generate(
          () -> {
            List<? extends SszData> children =
                containerSchema.getChildSchemas().stream()
                    .map(SszListTest::randomData)
                    .collect(Collectors.toList());
            return (T) containerSchema.createFromFields(children);
          });
    } else if (schema instanceof SszCompositeSchema) {
      return Stream.generate(
          () -> {
            SszCompositeSchema<SszComposite<SszData>> compositeSchema =
                (SszCompositeSchema<SszComposite<SszData>>) schema;
            int maxChildrenToAdd = (int) Long.min(compositeSchema.getMaxLength(), 16 * 1024);
            SszMutableComposite<SszData> writableCopy =
                compositeSchema.getDefault().createWritableCopy();
            for (int i = 0; i < maxChildrenToAdd; i++) {
              SszSchema<?> childSchema = compositeSchema.getChildSchema(i);
              SszData sszData = randomData(childSchema);
              writableCopy.set(i, sszData);
            }
            return (T) writableCopy.commitChanges();
          });
    } else {
      throw new IllegalArgumentException("Unknown schema: " + schema);
    }
  }

  static Stream<Arguments> testPrimitiveBitListTypeParameters() {
    return Stream.of(
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 0L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 1L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 2L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 3L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 4L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 5L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 6L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 7L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 8L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 9L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 10L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 11L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 12L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 13L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 14L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 15L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 16L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 17L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 255L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 256L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 257L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 511L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 512L),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 513L));
  }

  static Stream<Arguments> testPrimitiveNonBitListTypeParameters() {
    return Stream.of(
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 0L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 1L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 5L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 16L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 31L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 32L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 33L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 63L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 64L),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 65L),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 7L),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 8L),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 9L),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 15L),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 16L),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 17L),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 3L),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 4L),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 5L),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 7L),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 8L),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 9L),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 0L),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 1L),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 2L),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 3L));
  }

  static Stream<Arguments> testComplexStaticTypeParameters() {
    return Stream.of(
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 0L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 1L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 31L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 32L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 33L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 0L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 1L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 31L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 32L),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 33L),
        Arguments.of(TestDoubleSuperContainer.SSZ_SCHEMA, 0L),
        Arguments.of(TestDoubleSuperContainer.SSZ_SCHEMA, 1L),
        Arguments.of(TestDoubleSuperContainer.SSZ_SCHEMA, 5L));
  }

  static Stream<Arguments> testComplexVariableSizeTypeParameters() {
    return Stream.of(
        Arguments.of(VariableSizeContainer.SSZ_SCHEMA, 0L),
        Arguments.of(VariableSizeContainer.SSZ_SCHEMA, 1L),
        Arguments.of(VariableSizeContainer.SSZ_SCHEMA, 2L),
        Arguments.of(VariableSizeContainer.SSZ_SCHEMA, 3L),
        Arguments.of(VariableSizeContainer.SSZ_SCHEMA, 4L),
        Arguments.of(VariableSizeContainer.SSZ_SCHEMA, 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 0), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 1), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 32), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 33), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 63), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 64), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 65), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BYTES4_SCHEMA, 0), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BYTES4_SCHEMA, 1), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BYTES4_SCHEMA, 7), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BYTES4_SCHEMA, 8), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BYTES4_SCHEMA, 9), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BYTES4_SCHEMA, 15), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BYTES4_SCHEMA, 16), 5L),
        Arguments.of(new SszListSchema<>(SszPrimitiveSchemas.BYTES4_SCHEMA, 17), 5L));
  }

  static Stream<Arguments> testAllNonBitListTypeParameters() {
    return Stream.of(
            testPrimitiveNonBitListTypeParameters(),
            testComplexStaticTypeParameters(),
            testComplexVariableSizeTypeParameters())
        .flatMap(Function.identity());
  }

  static Stream<Arguments> testAllListTypeParameters() {
    return Stream.concat(testPrimitiveBitListTypeParameters(), testAllNonBitListTypeParameters());
  }

  @ParameterizedTest
  @MethodSource("testAllListTypeParameters")
  <T extends SszData> void clearTest1(SszSchema<T> listElementType, long maxLength) {
    if (maxLength == 0) {
      return;
    }

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);
    SszList<T> lr1 = sszListSchema.getDefault();
    SszMutableList<T> lw1 = lr1.createWritableCopy();
    lw1.append(randomData(listElementType));
    if (maxLength > 1) {
      lw1.append(randomData(listElementType));
    }
    SszMutableList<T> lw2 = lw1.commitChanges().createWritableCopy();
    lw2.clear();
    SszList<T> lr2 = lw2.commitChanges();
    assertThat(lr1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    SszMutableList<T> lw3 = lw1.commitChanges().createWritableCopy();
    lw3.clear();
    lw3.append(randomData(listElementType));
    SszList<T> lr3 = lw3.commitChanges();
    assertThat(lr3.size()).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("testAllListTypeParameters")
  <T extends SszData> void testListSszDeserializeFailsFastWithTooLongData(
      SszSchema<T> listElementType, long maxLengthL) {

    if (maxLengthL > 1024) {
      return;
    }
    int maxLength = (int) maxLengthL;

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);
    SszListSchema<T> largerSszListSchema = new SszListSchema<>(listElementType, maxLength + 10);

    // should normally deserialize smaller lists
    for (int i = max(0, maxLength - 8); i <= maxLength; i++) {
      SszMutableList<T> writableCopy = largerSszListSchema.getDefault().createWritableCopy();
      for (int j = 0; j < i; j++) {
        writableCopy.append(listElementType.getDefault());
      }
      Bytes ssz = writableCopy.commitChanges().sszSerialize();
      SszList<T> resList = sszListSchema.sszDeserialize(ssz);

      assertThat(resList.size()).isEqualTo(i);
    }

    // should fail fast when ssz is longer than max
    for (int i = maxLength + 1; i < maxLength + 10; i++) {
      SszMutableList<T> writableCopy = largerSszListSchema.getDefault().createWritableCopy();
      for (int j = 0; j < i; j++) {
        writableCopy.append(listElementType.getDefault());
      }
      Bytes ssz = writableCopy.commitChanges().sszSerialize();

      SszReader sszReader = SszReader.fromBytes(ssz);
      assertThatThrownBy(() -> sszListSchema.sszDeserialize(sszReader))
          .isInstanceOf(SszDeserializeException.class);
      if (listElementType.getBitsSize() >= 8 || i > maxLength + 8) {
        assertThat(sszReader.getAvailableBytes()).isGreaterThan(0);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("testPrimitiveNonBitListTypeParameters")
  <T extends SszData> void testNonBitEmptyListSsz(SszSchema<T> listElementType, long maxLength) {

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.sszSerialize()).isEqualTo(Bytes.EMPTY);

    SszList<T> emptyList1 = sszListSchema.sszDeserialize(Bytes.EMPTY);
    assertThat(emptyList1).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("testPrimitiveBitListTypeParameters")
  <T extends SszData> void testBitEmptyListSsz(SszSchema<T> listElementType, long maxLength) {

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.sszSerialize()).isEqualTo(Bytes.of(1));

    SszList<T> emptyList1 = sszListSchema.sszDeserialize(Bytes.of(1));
    assertThat(emptyList1).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("testAllListTypeParameters")
  <T extends SszData> void testEmptyListHash(SszSchema<T> listElementType, long maxLength) {

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.hashTreeRoot())
        .isEqualTo(
            Hash.sha2_256(
                Bytes.concatenate(
                    TreeUtil.ZERO_TREES[sszListSchema.treeDepth()].hashTreeRoot(), Bytes32.ZERO)));
  }

  @ParameterizedTest
  @MethodSource("testAllListTypeParameters")
  <T extends SszData> void testSszRoundtrip(SszSchema<T> listElementType, long maxLength) {

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);

    LongStream.of(1, 2, 3, 4, 5, maxLength - 1, maxLength)
        .takeWhile(i -> i <= maxLength)
        .takeWhile(i -> i < 1024)
        .forEach(
            size -> {
              SszList<T> list =
                  sszListSchema.ofElements(
                      randomDataStream(listElementType).limit(size).collect(Collectors.toList()));
              Bytes ssz = list.sszSerialize();
              SszList<T> list1 = sszListSchema.sszDeserialize(ssz);
              assertThat(SszTestUtils.equalsByGetters(list, list1)).isTrue();
              assertThat(list1.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
              Bytes ssz1 = list1.sszSerialize();
              assertThat(ssz1).isEqualTo(ssz);
            });
  }
}
