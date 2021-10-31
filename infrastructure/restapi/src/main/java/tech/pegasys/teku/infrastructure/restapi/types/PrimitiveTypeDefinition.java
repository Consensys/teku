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

package tech.pegasys.teku.infrastructure.restapi.types;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PrimitiveTypeDefinition<T> implements TypeDefinition<T> {

  public static final PrimitiveTypeDefinition<String> STRING_TYPE =
      new PrimitiveTypeDefinition<>("string", new StringDeserializer(), new StringSerializer());
  public static final PrimitiveTypeDefinition<UInt64> UINT64_TYPE =
      new PrimitiveTypeDefinition<>("string", new UInt64Deserializer(), new UInt64Serializer());

  private final String type;
  private final JsonDeserializer<T> valueParser;
  private final JsonSerializer<T> valueSerializer;

  private PrimitiveTypeDefinition(
      final String type,
      final JsonDeserializer<T> valueParser,
      final JsonSerializer<T> valueSerializer) {
    this.type = type;
    this.valueParser = valueParser;
    this.valueSerializer = valueSerializer;
  }

  @Override
  public JsonDeserializer<T> getDeserializer() {
    return valueParser;
  }

  @Override
  public JsonSerializer<T> getSerializer() {
    return valueSerializer;
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", type);
    gen.writeEndObject();
  }
}
