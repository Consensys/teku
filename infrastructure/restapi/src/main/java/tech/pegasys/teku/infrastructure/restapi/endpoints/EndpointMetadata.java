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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.JSON_CONTENT_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.HandlerType;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequestBodyException;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiResponse;

public class EndpointMetadata {
  private final HandlerType method;
  private final String path;
  private final String operationId;
  private final String summary;
  private final Optional<String> security;
  private final String description;
  private final boolean deprecated;
  private final Map<String, OpenApiResponse> responses;
  private final Optional<OpenApiTypeDefinition> requestBodyType;
  private final Optional<BodyTypeSelector<?>> requestBodyTypeSelector;
  private final List<String> tags;
  private final Map<String, StringValueTypeDefinition<?>> pathParams;
  private final Map<String, StringValueTypeDefinition<?>> requiredQueryParams;
  private final Map<String, StringValueTypeDefinition<?>> queryParams;

  private EndpointMetadata(
      final HandlerType method,
      final String path,
      final String operationId,
      final String summary,
      final Optional<String> security,
      final String description,
      final boolean deprecated,
      final Map<String, OpenApiResponse> responses,
      final Optional<OpenApiTypeDefinition> requestBodyType,
      final Optional<BodyTypeSelector<?>> requestBodyTypeSelector,
      final List<String> tags,
      final Map<String, StringValueTypeDefinition<?>> pathParams,
      final Map<String, StringValueTypeDefinition<?>> queryParams,
      final Map<String, StringValueTypeDefinition<?>> requiredQueryParams) {
    this.method = method;
    this.path = path;
    this.operationId = operationId;
    this.summary = summary;
    this.security = security;
    this.description = description;
    this.deprecated = deprecated;
    this.responses = responses;
    this.requestBodyType = requestBodyType;
    this.requestBodyTypeSelector = requestBodyTypeSelector;
    this.tags = tags;
    this.pathParams = pathParams;
    this.queryParams = queryParams;
    this.requiredQueryParams = requiredQueryParams;
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

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  public <T> T getRequestBody(final String body) throws JsonProcessingException {
    checkArgument(requestBodyType.isPresent(), "requestBodyType has not been defined");
    checkArgument(
        requestBodyTypeSelector.isPresent(), "requestBodyTypeSelector has not been defined");

    final DeserializableTypeDefinition<T> bodySchema =
        (DeserializableTypeDefinition<T>) getRequestBodyType(body);

    if (bodySchema == null) {
      throw new MissingRequestBodyException();
    }

    if (!getRequestBodyType().isEquivalentToDeserializableType(bodySchema)) {
      throw new IllegalStateException(
          "schema determined for parsing request body is not listed in requestBodyTypes");
    }

    final T result = JsonUtil.parse(body, bodySchema);
    if (result == null) {
      throw new MissingRequestBodyException();
    }
    return result;
  }

  public interface BodyTypeSelector<T> {
    DeserializableTypeDefinition<?> selectType(final String jsonContent);
  }

  public HandlerType getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  public Optional<String> getSecurity() {
    return security;
  }

  public List<String> getTags() {
    return tags;
  }

  public StringValueTypeDefinition<?> getPathParameterDefinition(final String parameterName) {
    checkArgument(
        pathParams.containsKey(parameterName),
        "Path parameter " + parameterName + " was not found in endpoint metadata");
    return pathParams.get(parameterName);
  }

  public StringValueTypeDefinition<?> getQueryParameterDefinition(final String parameterName) {
    checkArgument(
        requiredQueryParams.containsKey(parameterName) || queryParams.containsKey(parameterName),
        "Query parameter " + parameterName + " was not found in endpoint metadata");
    if (requiredQueryParams.containsKey(parameterName)) {
      return requiredQueryParams.get(parameterName);
    }
    return queryParams.get(parameterName);
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
    writeTags(gen);
    gen.writeStringField("operationId", operationId);
    gen.writeStringField("summary", summary);
    gen.writeStringField("description", description);
    if (deprecated) {
      gen.writeBooleanField("deprecated", true);
    }
    if (pathParams.size() > 0 || queryParams.size() > 0 || requiredQueryParams.size() > 0) {
      gen.writeArrayFieldStart("parameters");
      writeParameters(gen, pathParams, "path", true);
      writeParameters(gen, requiredQueryParams, "query", true);
      writeParameters(gen, queryParams, "query", false);
      gen.writeEndArray();
    }

    if (requestBodyType.isPresent()) {
      final OpenApiTypeDefinition content = requestBodyType.get();
      gen.writeObjectFieldStart("requestBody");
      gen.writeObjectFieldStart("content");
      gen.writeObjectFieldStart(JSON_CONTENT_TYPE);
      gen.writeFieldName("schema");
      content.serializeOpenApiTypeOrReference(gen);
      gen.writeEndObject();

      gen.writeEndObject();
      gen.writeEndObject();
    }

    if (security.isPresent()) {
      gen.writeArrayFieldStart("security");
      gen.writeStartObject();
      gen.writeArrayFieldStart(security.get());
      gen.writeEndArray();
      gen.writeEndObject();
      gen.writeEndArray();
    }

    gen.writeObjectFieldStart("responses");
    for (Entry<String, OpenApiResponse> responseEntry : responses.entrySet()) {
      gen.writeFieldName(responseEntry.getKey());
      responseEntry.getValue().writeOpenApi(gen);
    }
    gen.writeEndObject();
    gen.writeEndObject();
  }

  private void writeParameters(
      final JsonGenerator gen,
      final Map<String, StringValueTypeDefinition<?>> fields,
      final String parameterUsedIn,
      final boolean isMandatoryField)
      throws IOException {
    for (Map.Entry<String, StringValueTypeDefinition<?>> entry : fields.entrySet()) {
      gen.writeStartObject();
      gen.writeObjectField("name", entry.getKey());
      if (isMandatoryField) {
        gen.writeObjectField("required", true);
      }
      gen.writeObjectField("in", parameterUsedIn);
      gen.writeFieldName("schema");
      entry.getValue().serializeOpenApiTypeOrReference(gen);
      gen.writeEndObject();
    }
  }

  private void writeTags(final JsonGenerator gen) throws IOException {
    if (tags.isEmpty()) {
      return;
    }
    gen.writeArrayFieldStart("tags");

    for (String tag : tags) {
      gen.writeString(tag);
    }
    gen.writeEndArray();
  }

  public Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions() {
    return Stream.concat(
            responses.values().stream()
                .flatMap(response -> response.getReferencedTypeDefinitions().stream()),
            requestBodyType.stream()
                .flatMap(bodyType -> bodyType.getSelfAndReferencedTypeDefinitions().stream()))
        .collect(Collectors.toSet());
  }

  OpenApiTypeDefinition getRequestBodyType() {
    return requestBodyType.orElseThrow();
  }

  DeserializableTypeDefinition<?> getRequestBodyType(final String json) {
    final BodyTypeSelector<?> selector = requestBodyTypeSelector.orElseThrow();
    return selector.selectType(json);
  }

  public static class EndpointMetaDataBuilder {
    private HandlerType method;
    private String path;
    private String operationId;
    private String summary;
    private String description;
    private boolean deprecated = false;
    private final Map<String, StringValueTypeDefinition<?>> pathParams = new LinkedHashMap<>();
    private final Map<String, StringValueTypeDefinition<?>> queryParams = new LinkedHashMap<>();
    private final Map<String, StringValueTypeDefinition<?>> requiredQueryParams =
        new LinkedHashMap<>();
    private Optional<String> security = Optional.empty();
    private Optional<OpenApiTypeDefinition> requestBodyType = Optional.empty();
    private Optional<BodyTypeSelector<?>> bodyTypeSelector = Optional.empty();
    private final Map<String, OpenApiResponse> responses = new LinkedHashMap<>();
    private List<String> tags = Collections.emptyList();

    public EndpointMetaDataBuilder method(final HandlerType method) {
      this.method = method;
      return this;
    }

    public EndpointMetaDataBuilder path(final String path) {
      this.path = path;
      return this;
    }

    public EndpointMetaDataBuilder pathParam(final ParameterMetadata<?> parameterMetadata) {
      if (pathParams.containsKey(parameterMetadata.getName())) {
        throw new IllegalStateException(
            "Path Parameters already contains " + parameterMetadata.getName());
      }
      pathParams.put(parameterMetadata.getName(), parameterMetadata.getType());
      return this;
    }

    public EndpointMetaDataBuilder queryParam(final ParameterMetadata<?> parameterMetadata) {
      final String param = parameterMetadata.getName();
      if (queryParams.containsKey(param) || requiredQueryParams.containsKey(param)) {
        throw new IllegalStateException("Query parameters already contains " + param);
      }
      queryParams.put(parameterMetadata.getName(), parameterMetadata.getType());
      return this;
    }

    public EndpointMetaDataBuilder queryParamRequired(
        final ParameterMetadata<?> parameterMetadata) {
      final String param = parameterMetadata.getName();
      if (queryParams.containsKey(param) || requiredQueryParams.containsKey(param)) {
        throw new IllegalStateException("Query parameters already contains " + param);
      }
      requiredQueryParams.put(parameterMetadata.getName(), parameterMetadata.getType());
      return this;
    }

    public EndpointMetaDataBuilder withBearerAuthSecurity() {
      return security("bearerAuth");
    }

    public EndpointMetaDataBuilder security(final String security) {
      this.security = Optional.ofNullable(security);
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

    public EndpointMetaDataBuilder deprecated(final boolean deprecated) {
      this.deprecated = deprecated;
      return this;
    }

    public EndpointMetaDataBuilder response(final int responseCode, final String description) {
      return response(responseCode, description, emptyMap());
    }

    public EndpointMetaDataBuilder requestBodyType(
        final DeserializableTypeDefinition<?> requestBodyType) {
      this.requestBodyType = Optional.of(requestBodyType);
      this.bodyTypeSelector = Optional.of((__) -> requestBodyType);
      return this;
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableOneOfTypeDefinition<T> requestBodyType,
        final BodyTypeSelector<T> bodyTypeSelector) {
      this.requestBodyType = Optional.of(requestBodyType);
      this.bodyTypeSelector = Optional.of(bodyTypeSelector);
      return this;
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<?> content) {
      return response(responseCode, description, Map.of(JSON_CONTENT_TYPE, content));
    }

    public EndpointMetaDataBuilder withUnauthorizedResponse() {
      return response(SC_UNAUTHORIZED, "Unauthorized, no token is found", HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withNotFoundResponse() {
      return response(SC_NOT_FOUND, "Not found", HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withForbiddenResponse() {
      return response(
          SC_FORBIDDEN, "Forbidden, a token is found but is invalid", HTTP_ERROR_RESPONSE_TYPE);
    }

    public EndpointMetaDataBuilder withAuthenticationResponses() {
      return withUnauthorizedResponse().withForbiddenResponse();
    }

    public EndpointMetaDataBuilder withInternalErrorResponse() {
      response(SC_INTERNAL_SERVER_ERROR, "Internal server error", HTTP_ERROR_RESPONSE_TYPE);
      return this;
    }

    public EndpointMetaDataBuilder withBadRequestResponse(Optional<String> maybeMessage) {
      response(
          SC_BAD_REQUEST,
          maybeMessage.orElse(
              "The request could not be processed, check the response for more information."),
          HTTP_ERROR_RESPONSE_TYPE);
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
          method,
          path,
          operationId,
          summary,
          security,
          description,
          deprecated,
          responses,
          requestBodyType,
          bodyTypeSelector,
          tags,
          pathParams,
          queryParams,
          requiredQueryParams);
    }

    public EndpointMetaDataBuilder tags(final String... tags) {
      this.tags = List.of(tags);
      return this;
    }
  }
}
