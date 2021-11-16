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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.JSON_CONTENT_TYPE;

import com.fasterxml.jackson.core.JsonGenerator;
import io.javalin.http.HandlerType;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiResponse;
import tech.pegasys.teku.infrastructure.restapi.types.CoreTypes;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;

public class EndpointMetadata {
  private final HandlerType method;
  private final String path;
  private final String operationId;
  private final String summary;
  private final String description;
  private final Map<String, OpenApiResponse> responses;
  private final Optional<DeserializableTypeDefinition<?>> requestBodyType;

  private EndpointMetadata(
      final HandlerType method,
      final String path,
      final String operationId,
      final String summary,
      final String description,
      final Map<String, OpenApiResponse> responses,
      final Optional<DeserializableTypeDefinition<?>> requestBodyType) {
    this.method = method;
    this.path = path;
    this.operationId = operationId;
    this.summary = summary;
    this.description = description;
    this.responses = responses;
    this.requestBodyType = requestBodyType;
  }

  public static EndpointMetaDataBuilder get(final String path) {
    return new EndpointMetaDataBuilder().method(HandlerType.GET).path(path);
  }

  public static EndpointMetaDataBuilder post(final String path) {
    return new EndpointMetaDataBuilder().method(HandlerType.POST).path(path);
  }

  public static EndpointMetaDataBuilder delete(final String path) {
    return new EndpointMetaDataBuilder().method(HandlerType.DELETE).path(path);
  }

  public HandlerType getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  public SerializableTypeDefinition<?> getResponseType(
      final int statusCode, final String contentType) {
    final OpenApiResponse response = responses.get(Integer.toString(statusCode));
    checkArgument(response != null, "Unexpected response for status code %s", statusCode);

    final SerializableTypeDefinition<?> responseType = response.getType(contentType);
    checkArgument(
        responseType != null,
        "Unexpected content type %s for status code %s",
        contentType,
        statusCode);
    return responseType;
  }

  public void writeOpenApi(final JsonGenerator gen) throws IOException {
    gen.writeObjectFieldStart(method.name().toLowerCase(Locale.ROOT));
    gen.writeStringField("operationId", operationId);
    gen.writeStringField("summary", summary);
    gen.writeStringField("description", description);

    if (requestBodyType.isPresent()) {
      final DeserializableTypeDefinition<?> content = requestBodyType.get();
      gen.writeObjectFieldStart("requestBody");
      gen.writeObjectFieldStart("content");
      gen.writeObjectFieldStart(JSON_CONTENT_TYPE);
      gen.writeFieldName("schema");
      content.serializeOpenApiTypeOrReference(gen);
      gen.writeEndObject();

      gen.writeEndObject();
      gen.writeEndObject();
    }

    gen.writeObjectFieldStart("responses");
    for (Entry<String, OpenApiResponse> responseEntry : responses.entrySet()) {
      gen.writeFieldName(responseEntry.getKey());
      responseEntry.getValue().writeOpenApi(gen);
    }
    gen.writeEndObject();
    gen.writeEndObject();
  }

  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return Stream.concat(
            responses.values().stream()
                .flatMap(response -> response.getReferencedTypeDefinitions().stream()),
            requestBodyType.stream()
                .flatMap(bodyType -> bodyType.getSelfAndReferencedTypeDefinitions().stream()))
        .collect(Collectors.toSet());
  }

  public DeserializableTypeDefinition<?> getRequestBodyType() {
    return requestBodyType.orElseThrow();
  }

  public static class EndpointMetaDataBuilder {
    private HandlerType method;
    private String path;
    private String operationId;
    private String summary;
    private String description;
    private Optional<DeserializableTypeDefinition<?>> requestBodyType = Optional.empty();
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

    public EndpointMetaDataBuilder requestBodyType(
        final DeserializableTypeDefinition<?> requestBodyType) {
      this.requestBodyType = Optional.of(requestBodyType);
      return this;
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<?> content) {
      return response(responseCode, description, Map.of(JSON_CONTENT_TYPE, content));
    }

    public EndpointMetaDataBuilder withUnauthorizedResponse() {
      return response(
          SC_UNAUTHORIZED, "Unauthorized, no token is found", CoreTypes.HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withForbiddenResponse() {
      return response(
          SC_FORBIDDEN,
          "Forbidden, a token is found but is invalid",
          CoreTypes.HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withAuthenticationResponses() {
      return withUnauthorizedResponse().withForbiddenResponse();
    }

    public EndpointMetaDataBuilder withInternalErrorResponse() {
      return response(
          SC_INTERNAL_SERVER_ERROR, "Internal server error", CoreTypes.HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withBadRequestResponse(Optional<String> maybeMessage) {
      response(
          SC_BAD_REQUEST,
          maybeMessage.orElse(
              "The request could not be processed, check the response for more information."),
          CoreTypes.HTTP_ERROR_RESPONSE_TYPE);
      return this;
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

      if (!responses.containsKey(Integer.toString(SC_BAD_REQUEST))) {
        withBadRequestResponse(Optional.empty());
      }
      if (!responses.containsKey(Integer.toString(SC_INTERNAL_SERVER_ERROR))) {
        // add internal error response if a custom response hasn't been defined
        withInternalErrorResponse();
      }
      return new EndpointMetadata(
          method, path, operationId, summary, description, responses, requestBodyType);
    }
  }
}
