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

package tech.pegasys.teku.infrastructure.ssz.schema.json;

import tech.pegasys.teku.infrastructure.json.types.DeserializableObjectTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;

import java.util.List;
import java.util.Optional;

public class SszStableContainerTypeDefinition {

  public static <DataT extends SszStableContainer, SchemaT extends SszStableContainerSchema<DataT>>
      DeserializableTypeDefinition<DataT> createFor(final SchemaT schema) {
    final DeserializableObjectTypeDefinitionBuilder<DataT, StableContainerBuilder<DataT>> builder =
        DeserializableTypeDefinition.object();
    builder
        .name(schema.getContainerName())
        .initializer(() -> new StableContainerBuilder<>(schema))
        .finisher(StableContainerBuilder::build);
    final List<String> fieldNames = schema.getFieldNames();
    for (int fieldIndex = 0; fieldIndex < fieldNames.size(); fieldIndex++) {
      addField(schema, builder, fieldNames.get(fieldIndex), fieldIndex);
    }
    return builder.build();
  }

  private static <DataT extends SszStableContainer, SchemaT extends SszStableContainerSchema<DataT>>
      void addField(
          final SchemaT schema,
          final DeserializableObjectTypeDefinitionBuilder<DataT, StableContainerBuilder<DataT>> builder,
          final String childName,
          final int fieldIndex) {
    final SszSchema<?> childSchema = schema.getChildSchema(fieldIndex);
    if (childSchema.equals(SszPrimitiveSchemas.NONE_SCHEMA)) {
      return;
    }
    builder.withOptionalField(
        childName,
        childSchema.getJsonTypeDefinition(),
        value -> value.getAnyOptional(fieldIndex),
        (b, value) -> b.setValue(fieldIndex, value.orElseThrow()));
  }

  private static class StableContainerBuilder<DataT extends SszStableContainer> {
    private final SszStableContainerSchema<DataT> schema;
    private final SszData[] values;

    public StableContainerBuilder(final SszStableContainerSchema<DataT> schema) {
      this.schema = schema;
      this.values = new SszData[schema.getFieldsCount()];
    }

    public void setValue(final int childIndex, final SszData value) {
      values[childIndex] = value;
    }

    public DataT build() {
      return schema.createFromFieldValues(List.of(values));
    }
  }
}
