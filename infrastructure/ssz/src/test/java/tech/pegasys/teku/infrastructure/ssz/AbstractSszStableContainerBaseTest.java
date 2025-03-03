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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerBaseSchema;

public abstract class AbstractSszStableContainerBaseTest
    implements SszCompositeTestBase, SszMutableRefCompositeTestBase {

  @Override
  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  @Disabled
  public void set_shouldThrowWhenAppendingAboveMaxLen(final SszMutableComposite<SszData> data) {
    // doesn't apply to stable container
  }

  @Override
  public Stream<Arguments> sszMutableCompositeWithUpdateIndicesArguments() {
    return SszDataTestBase.passWhenEmpty(
        sszMutableComposites()
            .filter(data -> data.size() > 0)
            .map(data -> Arguments.of(data, streamValidIndices(data).boxed().toList())));
  }

  @Override
  public IntStream streamOutOfBoundsIndices(final SszComposite<?> data) {
    final SszStableContainerBaseSchema<?> schema =
        (SszStableContainerBaseSchema<?>) data.getSchema();

    final IntStream notAllowedStream =
        IntStream.range(0, schema.getMaxFieldCount()).filter(i -> !schema.isFieldAllowed(i));

    return IntStream.concat(
        notAllowedStream,
        IntStream.of(-1, (int) Long.min(Integer.MAX_VALUE, schema.getMaxFieldCount())));
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

    return activeFields
        .getSchema()
        .ofBits(
            IntStream.range(0, activeFields.getSchema().getLength())
                .filter(schema::isFieldAllowed)
                .filter(i -> !activeFields.getBit(i))
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
