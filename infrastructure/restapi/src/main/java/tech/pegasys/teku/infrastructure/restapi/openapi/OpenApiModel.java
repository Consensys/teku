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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tech.pegasys.teku.infrastructure.restapi.ApiDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.TypeDefinition;

public class OpenApiModel {

  private final String json;

  public OpenApiModel(final String json) {
    this.json = json;
  }

  public static OpenApiModel createFrom(final List<ApiDefinition<?>> apis) {
    // TODO: Build up the info, servers and tags section
    final Map<String, TypeDefinition<?>> schemas = new HashMap<>();
    apis.forEach(
        api -> {
          api.getParameters()
              .forEach(
                  param -> {
                    final TypeDefinition<?> paramType = param.getType();
                    paramType.getTypeName().ifPresent(typeName -> schemas.put(typeName, paramType));
                  });
          api.getBodyContent()
              .ifPresent(
                  body -> {
                    final TypeDefinition<?> content = body.getContent();
                    content.getTypeName().ifPresent(refName -> schemas.put(refName, content));
                  });
          api.getResponses().values().stream()
              .flatMap(response -> response.getContent().stream())
              .flatMap(typeMap -> typeMap.values().stream())
              .forEach(type -> type.getTypeName().ifPresent(refName -> schemas.put(refName, type)));
        });
    //   schemas
    //   parameters
    //   responses

    // headers?

    try {
      final ObjectMapper objectMapper = new ObjectMapper();
      final StringWriter writer = new StringWriter();
      final JsonGenerator gen = objectMapper.createGenerator(writer);
      gen.writeStartObject();
      gen.writeStringField("openapi", "3.0.1");

      // Info
      gen.writeObjectFieldStart("info");
      gen.writeStringField("title", "Teku");
      // blah blah blah
      gen.writeEndObject();

      // Paths
      gen.writeObjectFieldStart("paths");
      for (ApiDefinition<?> api : apis) {
        api.serializeOpenApi(gen, objectMapper.getSerializerProvider());
      }
      gen.writeEndObject();

      gen.writeObjectFieldStart("components");
      gen.writeObjectFieldStart("schemas");
      for (TypeDefinition<?> value : schemas.values()) {
        if (value.getTypeName().isPresent()) {
          gen.writeFieldName(value.getTypeName().get());
          value.serializeOpenApiType(gen, objectMapper.getSerializerProvider());
          // TODO: Need to be able to write out the subtypes this type uses.
        }
      }
      gen.writeEndObject();
      gen.writeEndObject();

      gen.writeEndObject();
      gen.flush();

      return new OpenApiModel(writer.toString());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public String toString() {
    return json;
  }
}
