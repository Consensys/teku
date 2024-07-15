/*
 * Copyright Consensys Software Inc., 2022
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

public interface SszCompositeTestBase extends SszDataTestBase {

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void get_childSchemaMatches(final SszComposite<?> data) {
    SszCompositeSchema<?> schema = data.getSchema();
    streamValidIndices(data)
        .forEach(
            i -> {
              SszData child = data.get(i);
              assertThat(child).as("child %s", i).isNotNull();
              SszSchema<?> childSchema = child.getSchema();
              assertThat(childSchema)
                  .as("child schema %s", i)
                  .isNotNull()
                  .isEqualTo(schema.getChildSchema(i));
            });
  }

  default IntStream streamOutOfBoundsIndices(final SszComposite<?> data) {
    return IntStream.of(
        -1, data.size(), (int) Long.min(Integer.MAX_VALUE, data.getSchema().getMaxLength()));
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void get_throwsOutOfBounds(final SszComposite<?> data) {
    streamOutOfBoundsIndices(data)
        .forEach(
            wrongIndex ->
                assertThatThrownBy(() -> data.get(wrongIndex))
                    .as("child %s", wrongIndex)
                    .isInstanceOf(IndexOutOfBoundsException.class));
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void size_shouldBeLessOrEqualThanMaxLength(final SszComposite<?> data) {
    Assertions.assertThat((long) data.size()).isLessThanOrEqualTo(data.getSchema().getMaxLength());
  }
}
