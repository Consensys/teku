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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyMap;

import com.fasterxml.jackson.core.JsonGenerator;
import io.javalin.http.HandlerType;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiResponse;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;

public class EndpointMetadata {
  private final HandlerType method;
  private final String path;
  private final String operationId;
  private final String summary;
  private final String description;
  private final Map<String, OpenApiResponse> responses;

  private EndpointMetadata(
      final HandlerType method,
      final String path,
      final String operationId,
      final String summary,
      final String description,
      final Map<String, OpenApiResponse> responses) {
    this.method = method;
    this.path = path;
    this.operationId = operationId;
    this.summary = summary;
    this.description = description;
    this.responses = responses;
  }

  public static EndpointMetaDataBuilder get(final String path) {
    return new EndpointMetaDataBuilder().method(HandlerType.GET).path(path);
  }

  public HandlerType getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  public void writeOpenApi(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart(method.name().toLowerCase(Locale.ROOT));
    gen.writeStringField("operationId", operationId);
    gen.writeStringField("summary", summary);
    gen.writeStringField("description", description);

    gen.writeObjectFieldStart("responses");
    for (Entry<String, OpenApiResponse> responseEntry : responses.entrySet()) {
      gen.writeFieldName(responseEntry.getKey());
      responseEntry.getValue().writeOpenApi(gen);
    }
    gen.writeEndObject();
    gen.writeEndObject();
  }

  public static class EndpointMetaDataBuilder {
    private HandlerType method;
    private String path;
    private String operationId;
    private String summary;
    private String description;
    private final Map<String, OpenApiResponse> responses = new LinkedHashMap<>();

    public EndpointMetaDataBuilder method(final HandlerType method) {
      this.method = method;
      return this;
    }

    public EndpointMetaDataBuilder path(final String path) {
      this.path = path;
      return this;
    }

    public EndpointMetaDataBuilder operationId(final String operationId) {
      this.operationId = operationId;
      return this;
    }

    public EndpointMetaDataBuilder summary(final String summary) {
      this.summary = summary;
      return this;
    }

    public EndpointMetaDataBuilder description(final String description) {
      this.description = description;
      return this;
    }

    public EndpointMetaDataBuilder response(final int responseCode, final String description) {
      return response(responseCode, description, emptyMap());
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<?> content) {
      return response(responseCode, description, Map.of("application/json", content));
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final Map<String, SerializableTypeDefinition<?>> content) {
      this.responses.put(Integer.toString(responseCode), new OpenApiResponse(description, content));
      return this;
    }

    public EndpointMetadata build() {
      checkNotNull(method, "method must be specified");
      checkNotNull(path, "path must be specified");
      checkNotNull(operationId, "operationId must be specified");
      checkNotNull(summary, "summary must be specified");
      checkNotNull(description, "description must be specified");
      checkState(!responses.isEmpty(), "Must specify at least one response");
      return new EndpointMetadata(method, path, operationId, summary, description, responses);
    }
  }
}
