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

package tech.pegasys.teku.ssz.backing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ssz.backing.SszDataAssert.assertThatSszData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszUInt64ListSchema;

public interface SszMutableCompositeTestBase extends SszCompositeTestBase {

  SszSchema<?> NON_EXISTING_SCHEMA = SszUInt64ListSchema.create(9283496234L);
  SszData NON_EXISTING_SCHEMA_DATA = NON_EXISTING_SCHEMA.getDefault();
  RandomSszDataGenerator generator = new RandomSszDataGenerator();

  default Stream<Arguments> sszMutableCompositeArguments() {
    return SszDataTestBase.passWhenEmpty(
        sszWritableData().map(SszData::createWritableCopy).map(Arguments::of));
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_throwsIndexOutOfBounds(SszMutableComposite<SszData> data) {
    assertThatThrownBy(() -> data.set(data.size() + 1, NON_EXISTING_SCHEMA_DATA))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> data.set(-1, NON_EXISTING_SCHEMA_DATA))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_throwsNPE(SszMutableComposite<SszData> data) {
    if (data.getSchema().getMaxLength() == 0) {
      return;
    }
    assertThatThrownBy(() -> data.set(0, null)).isInstanceOf(NullPointerException.class);
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldThrowWhenSchemaMismatch(SszMutableComposite<SszData> data) {
    for (int i = 0; i < data.size(); i++) {
      assertThatThrownBy(() -> data.set(0, NON_EXISTING_SCHEMA_DATA))
          .isInstanceOf(InvalidValueSchemaException.class);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldMatchGet(SszMutableComposite<SszData> data) {
    int[] updateIndexes =
        IntStream.concat(IntStream.range(1, 32), IntStream.of(data.size()))
            .filter(i -> i < data.size())
            .toArray();

    SszCompositeSchema<?> schema = data.getSchema();
    for (int i : updateIndexes) {
      SszData newChild = generator.randomData(schema.getChildSchema(i));
      data.set(i, newChild);

      assertThatSszData(data.get(i)).isEqualByAllMeansTo(newChild);

      SszComposite<SszData> data1 = data.commitChanges();

      assertThatSszData(data1.get(i)).isEqualByAllMeansTo(newChild);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldNotHaveSideEffects(SszMutableComposite<SszData> data) {
    List<Integer> updatedIndexes =
        IntStream.concat(IntStream.range(0, 2), IntStream.of(data.size() - 1))
            .distinct()
            .filter(i -> i >= 0 && i < data.size())
            .boxed()
            .collect(Collectors.toList());

    SszComposite<SszData> origData = data.commitChanges();

    SszCompositeSchema<?> schema = data.getSchema();
    List<SszData> newChildren =
        updatedIndexes.stream()
            .map(updatedIndex -> generator.randomData(schema.getChildSchema(updatedIndex)))
            .collect(Collectors.toList());

    List<SszComposite<SszData>> updatedData = new ArrayList<>();
    for (int i = 0; i < updatedIndexes.size(); i++) {
      Integer updateIndex = updatedIndexes.get(i);
      SszData oldValue = data.get(updateIndex);
      data.set(updateIndex, newChildren.get(i));
      SszComposite<SszData> data1 = data.commitChanges();
      updatedData.add(data1);
      data.set(updateIndex, oldValue);
    }
    SszComposite<SszData> data1 = data.commitChanges();

    assertThatSszData((SszComposite<SszData>) data).isEqualByGettersTo(origData);
    assertThatSszData(data1).isEqualByAllMeansTo(origData);

    for (int i = 0; i < updatedIndexes.size(); i++) {
      SszComposite<SszData> updated = updatedData.get(i);
      for (int i1 = 0; i1 < updated.size(); i1++) {
        if (i1 != updatedIndexes.get(i)) {
          assertThatSszData(updated.get(i1)).isEqualByAllMeansTo(origData.get(i1));
        } else {
          assertThatSszData(updated.get(i1)).isEqualByAllMeansTo(newChildren.get(i));
        }
      }
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldAppendOnExtendableStructures(SszMutableComposite<SszData> data) {
    if (data.size() < data.getSchema().getMaxLength()) {
      // the structure could be extended (just a List for now)
      int origSize = data.size();

      SszData appendChild = generator.randomData(data.getSchema().getChildSchema(origSize));
      data.set(origSize, appendChild);

      assertThat(data.size()).isEqualTo(origSize + 1);
      assertThatSszData(data.get(origSize)).isEqualByAllMeansTo(appendChild);

      SszComposite<SszData> data1 = data.commitChanges();
      assertThat(data1.size()).isEqualTo(origSize + 1);
      assertThatSszData(data1.get(origSize)).isEqualByAllMeansTo(appendChild);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldThrowWhenSetAboveSizeForExtendableStructures(
      SszMutableComposite<SszData> data) {
    if (data.size() < data.getSchema().getMaxLength()) {
      // the structure could be extended (just a List case for now)
      assertThatThrownBy(
              () ->
                  data.set(
                      data.size() + 1,
                      data.getSchema().getChildSchema(data.size() + 1).getDefault()))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldThrowWhenAppendingAboveMaxLen(SszMutableComposite<SszData> data) {
    long maxLength = data.getSchema().getMaxLength();
    if (maxLength <= 1024) {
      // don't want to waste too much time appending
      for (int i = data.size(); i < maxLength; i++) {
        data.set(i, data.getSchema().getChildSchema(i).getDefault());
      }
      assertThatThrownBy(
              () ->
                  data.set(
                      (int) maxLength,
                      data.getSchema().getChildSchema((int) maxLength).getDefault()))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void setInvalidator_shouldBeNotifiedOnSet(SszMutableComposite<SszData> data) {
    if (data.size() == 0) {
      return;
    }
    AtomicInteger invalidateCount = new AtomicInteger();
    data.setInvalidator(__ -> invalidateCount.incrementAndGet());
    data.set(0, generator.randomData(data.getSchema().getChildSchema(0)));

    assertThat(invalidateCount).hasValue(1);

    data.update(0, __ -> generator.randomData(data.getSchema().getChildSchema(0)));

    assertThat(invalidateCount).hasValue(2);
  }
}
