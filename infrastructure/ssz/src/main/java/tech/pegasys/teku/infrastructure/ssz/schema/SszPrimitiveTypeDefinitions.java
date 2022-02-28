/*
 * Copyright 2022 ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.json.types.BooleanTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;

public interface SszPrimitiveTypeDefinitions {
  DeserializableTypeDefinition<SszNone> SSZ_NONE_TYPE_DEFINITION =
      DeserializableTypeDefinition.string(SszNone.class)
          .formatter(Objects::toString)
          .parser(
              value -> {
                checkArgument(value == null, "SszNone must have null value");
                return null;
              })
          .example("null")
          .description("null value")
          .format("null")
          .build();

  DeserializableTypeDefinition<SszBit> SSZ_BIT_TYPE_DEFINTION =
      new SszTypeDefintionWrapper<>(SszPrimitiveSchemas.BIT_SCHEMA, new BooleanTypeDefinition());

  DeserializableTypeDefinition<SszByte> SSZ_BYTE_TYPE_DEFINITION =
      new SszTypeDefintionWrapper<>(SszPrimitiveSchemas.BYTE_SCHEMA, CoreTypes.BYTE_TYPE);

  DeserializableTypeDefinition<SszBytes32> SSZ_BYTES32_TYPE_DEFINITION =
      new SszTypeDefintionWrapper<>(SszPrimitiveSchemas.BYTES32_SCHEMA, CoreTypes.BYTES32_TYPE);

  DeserializableTypeDefinition<SszBytes4> SSZ_BYTES4_TYPE_DEFINITION =
      new SszTypeDefintionWrapper<>(SszPrimitiveSchemas.BYTES4_SCHEMA, CoreTypes.BYTES4_TYPE);

  DeserializableTypeDefinition<SszUInt64> SSZ_UINT64_TYPE_DEFINITION =
      new SszTypeDefintionWrapper<>(SszPrimitiveSchemas.UINT64_SCHEMA, CoreTypes.UINT64_TYPE);

  DeserializableTypeDefinition<SszUInt256> SSZ_UINT256_TYPE_DEFINITION =
      new SszTypeDefintionWrapper<>(SszPrimitiveSchemas.UINT256_SCHEMA, CoreTypes.UINT256_TYPE);

  class SszTypeDefintionWrapper<DataT, SszDataT extends SszPrimitive<DataT, SszDataT>>
      implements DeserializableTypeDefinition<SszDataT> {
    private final SszPrimitiveSchema<DataT, SszDataT> schema;
    private final DeserializableTypeDefinition<DataT> primitiveTypeDefinition;

    public SszTypeDefintionWrapper(
        final SszPrimitiveSchema<DataT, SszDataT> schema,
        final DeserializableTypeDefinition<DataT> primitiveTypeDefinition) {
      this.schema = schema;
      this.primitiveTypeDefinition = primitiveTypeDefinition;
    }

    @Override
    public SszDataT deserialize(final JsonParser parser) throws IOException {
      return schema.boxed(primitiveTypeDefinition.deserialize(parser));
    }

    @Override
    public void serializeOpenApiType(final JsonGenerator gen) throws IOException {
      primitiveTypeDefinition.serializeOpenApiType(gen);
    }

    @Override
    public void serialize(final SszDataT value, final JsonGenerator gen) throws IOException {
      primitiveTypeDefinition.serialize(value.get(), gen);
    }
  }
}
