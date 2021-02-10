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
  private static final Supplier<SszBytes4> bytes4Supplier = () -> new SszBytes4(
      Bytes4.rightPad(Bytes.random(4, random)));
  private static final Supplier<SszUInt64> uintSupplier = () -> SszUInt64.fromLong(random.nextLong());
  private static final Supplier<SszBytes32> bytes32Supplier = () -> new SszBytes32(Bytes32.random(random));

  @Test
  void clearTest() {
    SszListSchema<TestSubContainer> type =
        new SszListSchema<>(TestContainers.TestSubContainer.SSZ_SCHEMA, 100);
    SszList<TestSubContainer> lr1 = type.getDefault();
    SszMutableList<TestSubContainer> lw1 = lr1.createWritableCopy();
    lw1.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    lw1.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    SszMutableList<TestSubContainer> lw2 = lw1.commitChanges().createWritableCopy();
    lw2.clear();
    SszList<TestSubContainer> lr2 = lw2.commitChanges();
    assertThat(lr1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    SszMutableList<TestSubContainer> lw3 = lw1.commitChanges().createWritableCopy();
    lw3.clear();
    lw3.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    SszList<TestSubContainer> lr3 = lw3.commitChanges();
    assertThat(lr3.size()).isEqualTo(1);
  }

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

  @SuppressWarnings("unchecked")
  static <T extends SszData> Stream<T> randomData(SszSchema<T> schema) {
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
    } else if (schema instanceof SszCompositeSchema) {
//      SszCompositeSchema<T> compositeSchema = (SszCompositeSchema<T>) schema;
//      int childrenToAdd = (int) Long.min(compositeSchema.getMaxLength(), 1024);
//      compositeSchema.getDefault().createWritableCopy();
      throw new IllegalArgumentException("Unknown schema: " + schema);
    } else {
      throw new IllegalArgumentException("Unknown schema: " + schema);
    }
  }

  static Stream<Arguments> testPrimitiveBitListTypeParameters() {
    return Stream.of(
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 0, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 1, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 2, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 3, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 4, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 5, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 6, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 7, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 8, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 9, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 10, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 11, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 12, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 13, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 14, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 15, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 16, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 17, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 255, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 256, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 257, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 511, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 512, bitSupplier),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 513, bitSupplier));
  }

  static Stream<Arguments> testPrimitiveNonBitListTypeParameters() {
    return Stream.of(
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 0, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 1, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 5, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 16, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 31, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 32, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 33, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 63, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 64, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 65, byteSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 7, bytes4Supplier),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 8, bytes4Supplier),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 9, bytes4Supplier),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 15, bytes4Supplier),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 16, bytes4Supplier),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 17, bytes4Supplier),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 3, uintSupplier),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 4, uintSupplier),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 5, uintSupplier),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 7, uintSupplier),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 8, uintSupplier),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 9, uintSupplier),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 0, bytes32Supplier),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 1, bytes32Supplier),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 2, bytes32Supplier),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 3, bytes32Supplier));
  }

  static Stream<Arguments> testComplexStaticTypeParameters() {
    return Stream.of(
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 0),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 1),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 31),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 32),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 8), 33),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 0),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 1),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 31),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 32),
        Arguments.of(new SszVectorSchema<>(SszPrimitiveSchemas.BIT_SCHEMA, 5), 33),
        Arguments.of(TestDoubleSuperContainer.SSZ_SCHEMA, 0),
        Arguments.of(TestDoubleSuperContainer.SSZ_SCHEMA, 1),
        Arguments.of(TestDoubleSuperContainer.SSZ_SCHEMA, 5));
  }

  static Stream<Arguments> testComplexVariableSizeTypeParameters() {
    return Stream.of(
        Arguments.of(VariableSizeContainer.SSZ_SCHEMA, 5));
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
  <T extends SszData> void testListSszDeserializeFailsFastWithTooLongData(
      SszSchema<T> listElementType, int maxLength,
      Supplier<T> elementFactory) {

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
  <T extends SszData> void testNonBitEmptyListSsz(SszSchema<T> listElementType, int maxLength,
      Supplier<T> elementFactory) {

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.sszSerialize()).isEqualTo(Bytes.EMPTY);

    SszList<T> emptyList1 = sszListSchema.sszDeserialize(Bytes.EMPTY);
    assertThat(emptyList1).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("testPrimitiveBitListTypeParameters")
  <T extends SszData> void testBitEmptyListSsz(SszSchema<T> listElementType, int maxLength,
      Supplier<T> elementFactory) {

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.sszSerialize()).isEqualTo(Bytes.of(1));

    SszList<T> emptyList1 = sszListSchema.sszDeserialize(Bytes.of(1));
    assertThat(emptyList1).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("testAllListTypeParameters")
  <T extends SszData> void testEmptyListHash(SszSchema<T> listElementType, int maxLength,
      Supplier<T> elementFactory) {

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
  <T extends SszData> void testSszRoundtrip(SszSchema<T> listElementType, int maxLength,
      Supplier<T> elementFactory) {

    SszListSchema<T> sszListSchema = new SszListSchema<>(listElementType, maxLength);
    SszList<T> emptyList = sszListSchema.getDefault();

    assertThat(emptyList.hashTreeRoot())
        .isEqualTo(
            Hash.sha2_256(
                Bytes.concatenate(
                    TreeUtil.ZERO_TREES[sszListSchema.treeDepth()].hashTreeRoot(), Bytes32.ZERO)));
  }
}
