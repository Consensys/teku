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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.TestContainers.TestDoubleSuperContainer;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSubContainer;
import tech.pegasys.teku.ssz.backing.TestContainers.VariableSizeContainer;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

public class ListViewTest {

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

  static Stream<Arguments> testListSszDeserializeFailsFastWithTooLongDataParameters() {
    return Stream.of(
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 0),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 1),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 2),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 3),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 4),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 5),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 6),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 7),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 8),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 9),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 10),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 11),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 12),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 13),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 14),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 15),
        Arguments.of(SszPrimitiveSchemas.BIT_SCHEMA, 16),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 0),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 1),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 5),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 16),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 31),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 32),
        Arguments.of(SszPrimitiveSchemas.BYTE_SCHEMA, 33),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 7),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 8),
        Arguments.of(SszPrimitiveSchemas.BYTES4_SCHEMA, 9),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 3),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 4),
        Arguments.of(SszPrimitiveSchemas.UINT64_SCHEMA, 5),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 0),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 1),
        Arguments.of(SszPrimitiveSchemas.BYTES32_SCHEMA, 2),
        Arguments.of(TestDoubleSuperContainer.SSZ_SCHEMA, 5),
        Arguments.of(VariableSizeContainer.SSZ_SCHEMA, 5));
  }

  @ParameterizedTest
  @MethodSource("testListSszDeserializeFailsFastWithTooLongDataParameters")
  <T extends SszData> void testListSszDeserializeFailsFastWithTooLongData(
      SszSchema<T> listElementType, int maxLength) {

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
}
