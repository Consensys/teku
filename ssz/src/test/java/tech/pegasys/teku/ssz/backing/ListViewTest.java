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
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.ViewType;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

public class ListViewTest {

  @Test
  void clearTest() {
    ListViewType<TestSubContainer> type =
        new ListViewType<>(TestContainers.TestSubContainer.TYPE, 100);
    ListViewRead<TestSubContainer> lr1 = type.getDefault();
    ListViewWrite<TestSubContainer> lw1 = lr1.createWritableCopy();
    lw1.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    lw1.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewWrite<TestSubContainer> lw2 = lw1.commitChanges().createWritableCopy();
    lw2.clear();
    ListViewRead<TestSubContainer> lr2 = lw2.commitChanges();
    assertThat(lr1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    ListViewWrite<TestSubContainer> lw3 = lw1.commitChanges().createWritableCopy();
    lw3.clear();
    lw3.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewRead<TestSubContainer> lr3 = lw3.commitChanges();
    assertThat(lr3.size()).isEqualTo(1);
  }

  @Test
  void testMutableListReusable() {
    List<TestSubContainer> elements =
        IntStream.range(0, 5)
            .mapToObj(i -> new TestSubContainer(UInt64.valueOf(i), Bytes32.leftPad(Bytes.of(i))))
            .collect(Collectors.toList());

    ListViewType<TestSubContainer> type =
        new ListViewType<>(TestContainers.TestSubContainer.TYPE, 100);
    ListViewRead<TestSubContainer> lr1 = type.getDefault();
    ListViewWrite<TestSubContainer> lw1 = lr1.createWritableCopy();

    assertThat(lw1.sszSerialize()).isEqualTo(lr1.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr1.hashTreeRoot());

    lw1.append(elements.get(0));
    ListViewWrite<TestSubContainer> lw2 = type.getDefault().createWritableCopy();
    lw2.append(elements.get(0));
    ListViewRead<TestSubContainer> lr2 = lw2.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr2.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    lw1.appendAll(elements.subList(1, 5));
    ListViewWrite<TestSubContainer> lw3 = type.getDefault().createWritableCopy();
    lw3.appendAll(elements);
    ListViewRead<TestSubContainer> lr3 = lw3.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr3.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr3.hashTreeRoot());

    lw1.clear();

    assertThat(lw1.sszSerialize()).isEqualTo(lr1.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr1.hashTreeRoot());

    lw1.appendAll(elements.subList(0, 5));
    ListViewWrite<TestSubContainer> lw4 = type.getDefault().createWritableCopy();
    lw4.appendAll(elements);
    ListViewRead<TestSubContainer> lr4 = lw3.commitChanges();

    assertThat(lw1.sszSerialize()).isEqualTo(lr4.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr4.hashTreeRoot());

    lw1.clear();
    lw1.append(elements.get(0));

    assertThat(lw1.sszSerialize()).isEqualTo(lr2.sszSerialize());
    assertThat(lw1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());
  }

  static Stream<Arguments> testListSszDeserializeFailsFastWithTooLongDataParameters() {
    return Stream.of(
        Arguments.of(BasicViewTypes.BIT_TYPE, 0),
        Arguments.of(BasicViewTypes.BIT_TYPE, 1),
        Arguments.of(BasicViewTypes.BIT_TYPE, 2),
        Arguments.of(BasicViewTypes.BIT_TYPE, 3),
        Arguments.of(BasicViewTypes.BIT_TYPE, 4),
        Arguments.of(BasicViewTypes.BIT_TYPE, 5),
        Arguments.of(BasicViewTypes.BIT_TYPE, 6),
        Arguments.of(BasicViewTypes.BIT_TYPE, 7),
        Arguments.of(BasicViewTypes.BIT_TYPE, 8),
        Arguments.of(BasicViewTypes.BIT_TYPE, 9),
        Arguments.of(BasicViewTypes.BIT_TYPE, 10),
        Arguments.of(BasicViewTypes.BIT_TYPE, 11),
        Arguments.of(BasicViewTypes.BIT_TYPE, 12),
        Arguments.of(BasicViewTypes.BIT_TYPE, 13),
        Arguments.of(BasicViewTypes.BIT_TYPE, 14),
        Arguments.of(BasicViewTypes.BIT_TYPE, 15),
        Arguments.of(BasicViewTypes.BIT_TYPE, 16),
        Arguments.of(BasicViewTypes.BYTE_TYPE, 0),
        Arguments.of(BasicViewTypes.BYTE_TYPE, 1),
        Arguments.of(BasicViewTypes.BYTE_TYPE, 5),
        Arguments.of(BasicViewTypes.BYTE_TYPE, 16),
        Arguments.of(BasicViewTypes.BYTE_TYPE, 31),
        Arguments.of(BasicViewTypes.BYTE_TYPE, 32),
        Arguments.of(BasicViewTypes.BYTE_TYPE, 33),
        Arguments.of(BasicViewTypes.BYTES4_TYPE, 7),
        Arguments.of(BasicViewTypes.BYTES4_TYPE, 8),
        Arguments.of(BasicViewTypes.BYTES4_TYPE, 9),
        Arguments.of(BasicViewTypes.UINT64_TYPE, 3),
        Arguments.of(BasicViewTypes.UINT64_TYPE, 4),
        Arguments.of(BasicViewTypes.UINT64_TYPE, 5),
        Arguments.of(BasicViewTypes.BYTES32_TYPE, 0),
        Arguments.of(BasicViewTypes.BYTES32_TYPE, 1),
        Arguments.of(BasicViewTypes.BYTES32_TYPE, 2),
        Arguments.of(TestDoubleSuperContainer.TYPE, 5),
        Arguments.of(VariableSizeContainer.TYPE, 5));
  }

  @ParameterizedTest
  @MethodSource("testListSszDeserializeFailsFastWithTooLongDataParameters")
  <T extends ViewRead> void testListSszDeserializeFailsFastWithTooLongData(
      ViewType<T> listElementType, int maxLength) {

    ListViewType<T> listViewType = new ListViewType<>(listElementType, maxLength);
    ListViewType<T> largerListViewType = new ListViewType<>(listElementType, maxLength + 10);

    // should normally deserialize smaller lists
    for (int i = max(0, maxLength - 8); i <= maxLength; i++) {
      ListViewWrite<T> writableCopy = largerListViewType.getDefault().createWritableCopy();
      for (int j = 0; j < i; j++) {
        writableCopy.append(listElementType.getDefault());
      }
      Bytes ssz = writableCopy.commitChanges().sszSerialize();
      ListViewRead<T> resList = listViewType.sszDeserialize(ssz);

      assertThat(resList.size()).isEqualTo(i);
    }

    // should fail fast when ssz is longer than max
    for (int i = maxLength + 1; i < maxLength + 10; i++) {
      ListViewWrite<T> writableCopy = largerListViewType.getDefault().createWritableCopy();
      for (int j = 0; j < i; j++) {
        writableCopy.append(listElementType.getDefault());
      }
      Bytes ssz = writableCopy.commitChanges().sszSerialize();

      SszReader sszReader = SszReader.fromBytes(ssz);
      assertThatThrownBy(() -> listViewType.sszDeserialize(sszReader))
          .isInstanceOf(SSZDeserializeException.class);
      if (listElementType.getBitsSize() >= 8 || i > maxLength + 8) {
        assertThat(sszReader.getAvailableBytes()).isGreaterThan(0);
      }
    }
  }
}
