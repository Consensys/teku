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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.ClassUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.restapi.types.ObjectTypeDefinition.ObjectTypeDefinitionBuilder;

// TODO: Probably should split into Serializable and DeserializableTypeDefintion
public interface TypeDefinition<T> {

  static <T, B extends InternalTypeBuilder<T>> ObjectTypeDefinitionBuilder<T, B> object(
      final Supplier<B> builderFactory, final String name) {
    return new ObjectTypeDefinitionBuilder<>(builderFactory, name);
  }

  static <T> ObjectTypeDefinitionBuilder<T, ?> object(final String name) {
    return object(() -> null, name);
  }

  static <T> TypeDefinition<Map<String, T>> mapOf(final TypeDefinition<T> itemType) {
    return new MapTypeDefinition<>(itemType);
  }

  static <T> TypeDefinition<List<T>> listOf(final TypeDefinition<T> itemType) {
    final JsonDeserializer<List<T>> deserializer =
        new JsonDeserializer<>() {
          @Override
          public List<T> deserialize(final JsonParser p, final DeserializationContext ctxt)
              throws IOException, JsonProcessingException {
            if (!p.isExpectedStartArrayToken()) {
              final JsonToken t = p.currentToken();
              ctxt.handleUnexpectedToken(
                  handledType(),
                  t,
                  p,
                  "Unexpected token (%s), expected START_ARRAY for %s value",
                  t,
                  ClassUtil.nameOf(handledType()));
              return null;
            }
            final List<T> result = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
              result.add(itemType.getDeserializer().deserialize(p, ctxt));
            }
            return result;
          }
        };

    final JsonSerializer<List<T>> serializer =
        new JsonSerializer<>() {
          @Override
          public void serialize(
              final List<T> values, final JsonGenerator gen, final SerializerProvider serializers)
              throws IOException {
            gen.writeStartArray();
            for (T value : values) {
              itemType.getSerializer().serialize(value, gen, serializers);
            }
            gen.writeEndArray();
          }
        };
    return new TypeDefinition<>() {

      @Override
      public JsonDeserializer<List<T>> getDeserializer() {
        return deserializer;
      }

      @Override
      public JsonSerializer<List<T>> getSerializer() {
        return serializer;
      }

      @Override
      public void serializeOpenApiType(
          final JsonGenerator gen, final SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", "array");
        gen.writeFieldName("items");
        itemType.serializeOpenApiTypeOrReference(gen, serializers);
        gen.writeEndObject();
      }
    };
  }

  default Optional<String> getTypeName() {
    return Optional.empty();
  }

  // TODO: Maybe these should just be parse and serialize methods and scrap the separate interfaces

  JsonDeserializer<T> getDeserializer();

  JsonSerializer<T> getSerializer();

  void serializeOpenApiType(JsonGenerator gen, SerializerProvider serializers) throws IOException;

  default void serializeOpenApiTypeOrReference(
      final JsonGenerator gen, final SerializerProvider serializers) throws IOException {
    if (getTypeName().isPresent()) {
      gen.writeStartObject();
      gen.writeStringField("$ref", "#/components/schemas/" + getTypeName().get());
      gen.writeEndObject();
    } else {
      serializeOpenApiType(gen, serializers);
    }
  }
}
