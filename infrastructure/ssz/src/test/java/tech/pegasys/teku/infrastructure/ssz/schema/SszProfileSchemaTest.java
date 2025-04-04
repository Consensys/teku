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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.ssz.TestStableContainers.NESTED_PROFILE_STABLE_CONTAINER_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.TestStableContainers.NESTED_STABLE_CONTAINER_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.TestStableContainers.SHAPE_STABLE_CONTAINER_SCHEMA;

import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.RandomSszProfileSchemaGenerator;

public class SszProfileSchemaTest extends SszCompositeSchemaTestBase {

  public static Stream<SszProfileSchema<?>> testContainerSchemas() {

    return Stream.of(
            // generates 10 variation of profiles over SHAPE stable container with a random mix of
            // required and optionals
            new RandomSszProfileSchemaGenerator(SHAPE_STABLE_CONTAINER_SCHEMA)
                .randomProfileSchemasStream()
                .limit(10),

            // generates 10 variation of profiles over NESTED stable container with a random mix of
            // required and optionals
            new RandomSszProfileSchemaGenerator(NESTED_STABLE_CONTAINER_SCHEMA)
                .randomProfileSchemasStream()
                .limit(10),

            // generates 10 variation of profiles over PROFILE NESTED stable container with a random
            // mix of required and optionals
            new RandomSszProfileSchemaGenerator(NESTED_PROFILE_STABLE_CONTAINER_SCHEMA)
                .randomProfileSchemasStream()
                .limit(10),

            // a nested with all optionals
            new RandomSszProfileSchemaGenerator(NESTED_STABLE_CONTAINER_SCHEMA)
                .withMaxRequiredFields(0)
                .withMinOptionalFields(NESTED_STABLE_CONTAINER_SCHEMA.getFieldsCount())
                .randomProfileSchemasStream()
                .limit(1),

            // all required
            new RandomSszProfileSchemaGenerator(NESTED_STABLE_CONTAINER_SCHEMA)
                .withMinRequiredFields(NESTED_STABLE_CONTAINER_SCHEMA.getFieldsCount())
                .withMaxOptionalFields(0)
                .randomProfileSchemasStream()
                .limit(1))
        .flatMap(Function.identity());
  }

  @Override
  public Stream<SszProfileSchema<?>> testSchemas() {
    return testContainerSchemas();
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  @Override
  void getChildSchema_shouldThrowIndexOutOfBounds(final SszCompositeSchema<?> schema) {
    Assumptions.assumeThat(schema.getMaxLength()).isLessThan(Integer.MAX_VALUE);
    int tooBigIndex = ((SszProfileSchema<?>) schema).getChildrenNamedSchemas().size();
    assertThatThrownBy(() -> schema.getChildSchema(tooBigIndex))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }
}
