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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSubContainer;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

public class SszListTest implements SszListAbstractTest, SszMutableRefCompositeAbstractTest {

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

  @Disabled // TODO move to schema tests
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
}
