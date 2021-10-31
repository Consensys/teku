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

package tech.pegasys.teku.infrastructure.restapi.openapi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.restapi.types.TypeDefinition;

public class OpenApiResponse {
  private final String description;
  private final Optional<Map<String, TypeDefinition<?>>> content;

  public OpenApiResponse(
      final String description, final Optional<Map<String, TypeDefinition<?>>> content) {
    this.description = description;
    this.content = content;
  }

  public String getDescription() {
    return description;
  }

  public Optional<Map<String, TypeDefinition<?>>> getContent() {
    return content;
  }

  public static class OpenApiResponseTypeDefinition implements TypeDefinition<OpenApiResponse> {

    @Override
    public JsonDeserializer<OpenApiResponse> getDeserializer() {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public JsonSerializer<OpenApiResponse> getSerializer() {
      return new JsonSerializer<>() {
        @Override
        public void serialize(
            final OpenApiResponse value,
            final JsonGenerator gen,
            final SerializerProvider serializers)
            throws IOException {
          gen.writeStartObject();
          gen.writeStringField("description", value.description);
          if (value.content.isPresent()) {
            gen.writeObjectFieldStart("content");
            for (final Map.Entry<String, TypeDefinition<?>> contentEntry :
                value.content.get().entrySet()) {
              gen.writeFieldName(contentEntry.getKey());
              contentEntry.getValue().serializeOpenApiTypeOrReference(gen, serializers);
            }
            gen.writeEndObject();
          }
          gen.writeEndObject();
        }
      };
    }

    @Override
    public void serializeOpenApiType(
        final JsonGenerator gen, final SerializerProvider serializers) {
      throw new UnsupportedOperationException("Bit too meta for me...");
    }
  }
}
