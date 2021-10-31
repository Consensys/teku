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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class MapTypeDefinition<T> implements TypeDefinition<Map<String, T>> {

  private final TypeDefinition<T> itemType;

  public MapTypeDefinition(final TypeDefinition<T> itemType) {
    this.itemType = itemType;
  }

  @Override
  public JsonDeserializer<Map<String, T>> getDeserializer() {
    return new JsonDeserializer<>() {
      @Override
      public Map<String, T> deserialize(final JsonParser p, final DeserializationContext ctxt)
          throws IOException {
        final Map<String, T> result = new HashMap<>();
        JsonToken t = p.getCurrentToken();
        if (t == null) {
          t = p.nextToken();
        }
        if (t == JsonToken.START_OBJECT) {
          t = p.nextToken();
        }
        for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
          String fieldName = p.getCurrentName();
          t = p.nextToken();
          final T value = itemType.getDeserializer().deserialize(p, ctxt);
          result.put(fieldName, value);
        }
        return result;
      }
    };
  }

  @Override
  public JsonSerializer<Map<String, T>> getSerializer() {
    return new JsonSerializer<>() {
      @Override
      public void serialize(
          final Map<String, T> value, final JsonGenerator gen, final SerializerProvider serializers)
          throws IOException {
        gen.writeStartObject();
        for (Entry<String, T> entry : value.entrySet()) {
          gen.writeFieldName(entry.getKey());
          itemType.getSerializer().serialize(entry.getValue(), gen, serializers);
        }
        gen.writeEndObject();
      }
    };
  }

  @Override
  public void serializeOpenApiType(final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();
    gen.writeStringField("type", "object");
    gen.writeFieldName("additionalProperties");
    itemType.serializeOpenApiTypeOrReference(gen, serializers);
    gen.writeEndObject();
    gen.writeEndObject();
  }
}
