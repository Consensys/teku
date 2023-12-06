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

import com.google.common.collect.Streams;
import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
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

    public ContainerWithOptionals(boolean val) {
      super(SSZ_SCHEMA, SszBit.of(val));
    }
  }
}
