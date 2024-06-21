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

import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.json.types.DeserializableObjectTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszProfile;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.NamedIndexedSchema;

public class SszProfileTypeDefinition {

  public static <DataT extends SszProfile, SchemaT extends SszProfileSchema<DataT>>
      DeserializableTypeDefinition<DataT> createFor(final SchemaT schema) {
    final DeserializableObjectTypeDefinitionBuilder<DataT, ProfileBuilder<DataT>> builder =
        DeserializableTypeDefinition.object();
    builder
        .name(schema.getContainerName())
        .initializer(() -> new ProfileBuilder<>(schema))
        .finisher(ProfileBuilder::build);

    final List<? extends NamedIndexedSchema<?>> definedChildrenSchemas =
        schema.getDefinedChildrenSchemas();

    IntStream.range(0, schema.getActiveFieldCount())
        .forEach(
            activeFieldIndex -> {
              final int index = schema.getNthActiveFieldIndex(activeFieldIndex);
              final NamedIndexedSchema<?> namedSchema = definedChildrenSchemas.get(index);
              addField(
                  builder, namedSchema.getSchema(), namedSchema.getName(), index, activeFieldIndex);
            });
    return builder.build();
  }

  private static <DataT extends SszProfile> void addField(
      final DeserializableObjectTypeDefinitionBuilder<DataT, ProfileBuilder<DataT>> builder,
      final SszSchema<?> childSchema,
      final String childName,
      final int index,
      final int activeFieldIndex) {

    builder.withField(
        childName,
        childSchema.getJsonTypeDefinition(),
        value -> value.getAny(index),
        (b, value) -> b.setValue(activeFieldIndex, value));
  }

  private static class ProfileBuilder<DataT extends SszProfile> {
    private final SszProfileSchema<DataT> schema;
    private final SszData[] values;

    public ProfileBuilder(final SszProfileSchema<DataT> schema) {
      this.schema = schema;
      this.values = new SszData[schema.getActiveFieldCount()];
    }

    public void setValue(final int childIndex, final SszData value) {
      values[childIndex] = value;
    }

    public DataT build() {
      return schema.createFromFieldValues(List.of(values));
    }
  }
}
