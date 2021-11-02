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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.HandlerType;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.types.OpenApiTypeDefinition;

public class OpenApiDocBuilder {

  public static final String OPENAPI_VERSION = "3.0.1";

  // Info
  private String title;
  private String version;
  private String description;
  private String licenseName;
  private String licenseUrl;

  private final Map<String, Map<HandlerType, RestApiEndpoint>> endpoints = new LinkedHashMap<>();

  public OpenApiDocBuilder title(final String title) {
    this.title = title;
    return this;
  }

  public OpenApiDocBuilder version(final String version) {
    this.version = version;
    return this;
  }

  public OpenApiDocBuilder description(final String description) {
    this.description = description;
    return this;
  }

  public OpenApiDocBuilder license(final String licenseName, final String licenseUrl) {
    this.licenseName = licenseName;
    this.licenseUrl = licenseUrl;
    return this;
  }

  public OpenApiDocBuilder endpoint(final RestApiEndpoint endpoint) {
    this.endpoints
        .computeIfAbsent(endpoint.getMetadata().getPath(), __ -> new LinkedHashMap<>())
        .put(endpoint.getMetadata().getMethod(), endpoint);
    return this;
  }

  public String build() {
    checkNotNull(title, "title must be supplied");
    checkNotNull(version, "version must be supplied");
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator gen = new ObjectMapper().createGenerator(writer)) {

      gen.writeStartObject();
      gen.writeStringField("openapi", OPENAPI_VERSION);
      writeInfo(gen);
      writePaths(gen);

      writeComponents(gen);
      gen.writeEndObject();

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  private void writeInfo(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart("info");
    gen.writeStringField("title", title);
    if (description != null) {
      gen.writeStringField("description", description);
    }
    if (licenseName != null) {
      gen.writeObjectFieldStart("license");
      gen.writeStringField("name", licenseName);
      gen.writeStringField("url", licenseUrl);
      gen.writeEndObject();
    }
    gen.writeStringField("version", version);
    gen.writeEndObject();
  }

  private void writePaths(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart("paths");
    for (final Entry<String, Map<HandlerType, RestApiEndpoint>> pathEntry : endpoints.entrySet()) {
      gen.writeObjectFieldStart(pathEntry.getKey());
      for (RestApiEndpoint endpoint : pathEntry.getValue().values()) {
        final EndpointMetadata metadata = endpoint.getMetadata();
        metadata.writeOpenApi(gen);
      }
      gen.writeEndObject();
    }
    gen.writeEndObject();
  }

  private void writeComponents(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart("components");
    writeSchemas(gen);
    gen.writeEndObject();
  }

  private void writeSchemas(final JsonGenerator gen) throws IOException {
    final Set<OpenApiTypeDefinition> typeDefinitions =
        endpoints.values().stream()
            .flatMap(pathEndpoints -> pathEndpoints.values().stream())
            .flatMap(endpoint -> endpoint.getMetadata().getReferencedTypeDefinitions().stream())
            .filter(type -> type.getTypeName().isPresent())
            .collect(toSet());
    gen.writeObjectFieldStart("schemas");
    for (OpenApiTypeDefinition type : typeDefinitions) {
      gen.writeFieldName(type.getTypeName().orElseThrow());
      type.serializeOpenApiType(gen);
    }
    gen.writeEndObject();
  }
}
