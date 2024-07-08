/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.infrastructure.ssz;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.RandomSszDataGenerator.StableContainerMode;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerBaseSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchemaTest;

public class SszStableContainerTest
    implements SszCompositeTestBase, SszMutableRefCompositeTestBase {

  @Override
  public Stream<SszContainer> sszData() {
    RandomSszDataGenerator smallListsRandomSCGen = new RandomSszDataGenerator().withMaxListSize(1);
    RandomSszDataGenerator largeListsRandomSCGen =
        new RandomSszDataGenerator()
            .withStableContainerMode(StableContainerMode.RANDOM)
            .withMaxListSize(1024);
    RandomSszDataGenerator emptySCGen =
        new RandomSszDataGenerator().withStableContainerMode(StableContainerMode.EMPTY);
    RandomSszDataGenerator fullSCGen =
        new RandomSszDataGenerator().withStableContainerMode(StableContainerMode.FULL);

    RandomSszDataGenerator anotherRound =
        new RandomSszDataGenerator().withStableContainerMode(StableContainerMode.RANDOM);
    return SszStableContainerSchemaTest.testContainerSchemas()
        .flatMap(
            schema ->
                Stream.of(
                    schema.getDefault(),
                    smallListsRandomSCGen.randomData(schema),
                    largeListsRandomSCGen.randomData(schema),
                    emptySCGen.randomData(schema),
                    fullSCGen.randomData(schema),
                    // more combinations of active fields
                    anotherRound.randomData(schema),
                    anotherRound.randomData(schema)));
  }

  @Override
  public IntStream streamOutOfBoundsIndices(final SszComposite<?> data) {
    return IntStream.of(
        -1,
        (int)
            Long.min(
                Integer.MAX_VALUE,
                ((SszStableContainerBaseSchema<?>) data.getSchema()).getMaxFieldCount()));
  }

  @Override
  public IntStream streamValidIndices(final SszComposite<?> data) {
    final SszBitvector activeFields =
        ((SszStableContainerBaseSchema<?>) data.getSchema())
            .getActiveFieldsBitvectorFromBackingNode(data.getBackingNode());
    return activeFields.streamAllSetBits();
  }

  protected IntStream streamNotPresentIndices(final SszComposite<?> data) {
    final SszStableContainerBaseSchema<?> schema =
        ((SszStableContainerBaseSchema<?>) data.getSchema());
    final SszBitvector activeFields =
        schema.getActiveFieldsBitvectorFromBackingNode(data.getBackingNode());

    final int lastActiveIndex = activeFields.getLastSetBitIndex();
    return activeFields
        .getSchema()
        .ofBits(
            IntStream.range(0, activeFields.getSchema().getLength())
                .filter(i -> !activeFields.getBit(i) && i <= lastActiveIndex)
                .toArray())
        .streamAllSetBits();
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  void get_throwsNoSuchElement(final SszComposite<?> data) {
    streamNotPresentIndices(data)
        .forEach(
            wrongIndex ->
                assertThatThrownBy(() -> data.get(wrongIndex))
                    .as("child %s", wrongIndex)
                    .isInstanceOf(NoSuchElementException.class));
  }
}
