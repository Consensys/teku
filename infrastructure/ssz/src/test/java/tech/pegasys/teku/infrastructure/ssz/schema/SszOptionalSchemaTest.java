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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszOptionalSchemaTest extends SszSchemaTestBase {

  public static Stream<SszOptionalSchema<?>> testOptionalSchemas() {
    return Stream.of(
        SszOptionalSchema.create(SszPrimitiveSchemas.BIT_SCHEMA),
        SszOptionalSchema.create(SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32)),
        SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA),
        SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA),
        SszOptionalSchema.create(SszOptionalSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)));
  }

  public static Stream<SszSchema<?>> testContainerOptionalSchemas() {
    return Stream.of(ContainerWithOptionals.SSZ_SCHEMA);
  }

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return Streams.concat(testOptionalSchemas(), testContainerOptionalSchemas());
  }

  @ParameterizedTest(name = "schema={0}, value={1}")
  @MethodSource("getOptionalSchemaFixtures")
  public void testFixtures(
      final SszOptionalSchema<?> schema, final Optional<? extends SszData> value) {
    SszOptional fromValue = schema.createFromValue(value);
    assertThat(fromValue.getValue()).isEqualTo(value);
    SszOptionalSchema<SszOptional<SszByte>, SszByte> sszOptionalSszByteSszOptionalSchema = SszOptionalSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA);
    SszOptional<SszByte> fromValue1 = sszOptionalSszByteSszOptionalSchema.createFromValue(Optional.of(SszByte.of(12)));
  }

  static Stream<Arguments> getOptionalSchemaFixtures() {
    return Stream.of(
        Arguments.of(SszOptionalSchema.create(SszPrimitiveSchemas.UINT8_SCHEMA), Optional.empty()),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA), Optional.of(SszByte.of(12))),
        Arguments.of(
            SszOptionalSchema.create(SszPrimitiveSchemas.UINT8_SCHEMA),
            Optional.of(SszByte.of(12))));
  }

  public static class ContainerWithOptionals extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<ContainerWithOptionals> SSZ_SCHEMA =
        SszContainerSchema.create(
            "TestSmallContainer",
            List.of(
                AbstractSszContainerSchema.NamedSchema.of(
                    "a", SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA)),
                AbstractSszContainerSchema.NamedSchema.of("b", SszPrimitiveSchemas.BYTES32_SCHEMA),
                AbstractSszContainerSchema.NamedSchema.of(
                    "c", SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA)),
                AbstractSszContainerSchema.NamedSchema.of("d", SszPrimitiveSchemas.BYTES32_SCHEMA),
                AbstractSszContainerSchema.NamedSchema.of(
                    "e", SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA))),
            ContainerWithOptionals::new);

    private ContainerWithOptionals(
        SszContainerSchema<ContainerWithOptionals> type, TreeNode backingNode) {
      super(type, backingNode);
    }
  }
}
