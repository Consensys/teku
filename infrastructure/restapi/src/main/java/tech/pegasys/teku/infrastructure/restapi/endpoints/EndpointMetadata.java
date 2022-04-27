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
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.HandlerType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.function.IOFunction;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequestBodyException;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiResponse;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OctetStreamRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfJsonRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfJsonRequestContentTypeDefinition.BodyTypeSelector;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.RequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.SimpleJsonRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.JsonResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;

public class EndpointMetadata {
  private final HandlerType method;
  private final String path;
  private final String operationId;
  private final String summary;
  private final Optional<String> security;
  private final String description;
  private final boolean deprecated;
  private final String defaultResponseContentType;
  private final Map<String, OpenApiResponse> responses;
  private final String defaultRequestContentType;
  private final Map<String, RequestContentTypeDefinition<?>> requestBodyTypes;
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
      final String defaultResponseContentType,
      final Map<String, OpenApiResponse> responses,
      final String defaultRequestContentType,
      final Map<String, RequestContentTypeDefinition<?>> requestBodyTypes,
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
    this.defaultResponseContentType = defaultResponseContentType;
    this.responses = responses;
    this.defaultRequestContentType = defaultRequestContentType;
    this.requestBodyTypes = requestBodyTypes;
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
  public <T> T getRequestBody(final InputStream body, final Optional<String> maybeContentType)
      throws JsonProcessingException {
    checkArgument(!requestBodyTypes.isEmpty(), "requestBodyType has not been defined");

    try {
      final String contentType = selectRequestContentType(maybeContentType);
      final RequestContentTypeDefinition<T> requestContentTypeDefinition =
          (RequestContentTypeDefinition<T>) requestBodyTypes.get(contentType);
      final T result = requestContentTypeDefinition.deserialize(body);
      if (result == null) {
        throw new MissingRequestBodyException();
      }
      return result;
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String selectRequestContentType(final Optional<String> maybeContentType)
      throws BadRequestException {
    if (maybeContentType.isEmpty()) {
      return defaultRequestContentType;
    }
    return ContentTypes.getContentType(requestBodyTypes.keySet(), maybeContentType)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "Request content type "
                        + maybeContentType.get()
                        + " is not supported. Must be one of: "
                        + requestBodyTypes.keySet()));
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

  public ResponseContentTypeDefinition<?> getResponseType(
      final int statusCode, final String contentType) {
    final OpenApiResponse response = responses.get(Integer.toString(statusCode));
    checkArgument(response != null, "Unexpected response for status code %s", statusCode);

    final ResponseContentTypeDefinition<?> responseType = response.getType(contentType);
    checkArgument(
        responseType != null,
        "Unexpected content type %s for status code %s",
        contentType,
        statusCode);
    return responseType;
  }

  public String selectResponseContentType(
      final int statusCode, final Optional<String> acceptHeader) {
    final Collection<String> supportedTypes = getSupportedResponseContentTypes(statusCode);
    final String selectedType =
        ContentTypes.getContentType(supportedTypes, acceptHeader)
            .orElse(defaultResponseContentType);
    checkState(supportedTypes.contains(selectedType), "Default content type is not supported.");
    return selectedType;
  }

  private Collection<String> getSupportedResponseContentTypes(final int statusCode) {
    final OpenApiResponse openApiResponse = responses.get(Integer.toString(statusCode));
    if (openApiResponse == null) {
      throw new IllegalArgumentException("Status code " + statusCode + " not supported");
    }
    return openApiResponse.getSupportedContentTypes();
  }

  @SuppressWarnings("unchecked")
  public <T> byte[] serialize(final int statusCode, final String contentType, final T response)
      throws JsonProcessingException {
    final ResponseContentTypeDefinition<T> type =
        (ResponseContentTypeDefinition<T>) getResponseType(statusCode, contentType);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      type.serialize(response, out);
      return out.toByteArray();
    } catch (final JsonProcessingException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
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

    if (!requestBodyTypes.isEmpty()) {
      gen.writeObjectFieldStart("requestBody");
      gen.writeObjectFieldStart("content");
      for (Map.Entry<String, RequestContentTypeDefinition<?>> requestTypeEntry :
          requestBodyTypes.entrySet()) {
        gen.writeObjectFieldStart(requestTypeEntry.getKey());
        gen.writeFieldName("schema");
        requestTypeEntry.getValue().serializeOpenApiTypeOrReference(gen);
        gen.writeEndObject();
      }
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
            requestBodyTypes.values().stream()
                .flatMap(bodyType -> bodyType.getSelfAndReferencedTypeDefinitions().stream()))
        .collect(Collectors.toSet());
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
    private String defaultRequestType = ContentTypes.JSON;
    private final Map<String, RequestContentTypeDefinition<?>> requestBodyTypes = new HashMap<>();
    private String defaultResponseType = ContentTypes.JSON;
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

    public EndpointMetaDataBuilder defaultResponseType(final String defaultContentType) {
      this.defaultResponseType = defaultContentType;
      return this;
    }

    public EndpointMetaDataBuilder defaultRequestType(final String defaultRequestType) {
      this.defaultRequestType = defaultRequestType;
      return this;
    }

    public EndpointMetaDataBuilder requestBodyType(
        final DeserializableTypeDefinition<?> requestBodyType) {
      this.requestBodyTypes.put(
          ContentTypes.JSON, new SimpleJsonRequestContentTypeDefinition<>(requestBodyType));
      return this;
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableOneOfTypeDefinition<T> requestBodyType,
        final BodyTypeSelector<T> bodyTypeSelector) {
      this.requestBodyTypes.put(
          ContentTypes.JSON,
          new OneOfJsonRequestContentTypeDefinition<>(requestBodyType, bodyTypeSelector));
      return this;
    }

    public <T> EndpointMetaDataBuilder requestBodyType(
        final SerializableOneOfTypeDefinition<T> requestBodyType,
        final BodyTypeSelector<T> bodyTypeSelector,
        final IOFunction<Bytes, T> octetStreamParser) {
      this.requestBodyTypes.put(
          ContentTypes.JSON,
          new OneOfJsonRequestContentTypeDefinition<>(requestBodyType, bodyTypeSelector));
      this.requestBodyTypes.put(
          ContentTypes.OCTET_STREAM,
          OctetStreamRequestContentTypeDefinition.parseBytes(octetStreamParser));
      return this;
    }

    public EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<?> content) {
      return response(
          responseCode,
          description,
          Map.of(ContentTypes.JSON, new JsonResponseContentTypeDefinition<>(content)));
    }

    public <T> EndpointMetaDataBuilder response(
        final int responseCode,
        final String description,
        final SerializableTypeDefinition<T> content,
        final Function<T, Bytes> toOctetStreamBytes) {
      return response(
          responseCode,
          description,
          Map.of(
              ContentTypes.JSON,
              new JsonResponseContentTypeDefinition<>(content),
              ContentTypes.OCTET_STREAM,
              new OctetStreamResponseContentTypeDefinition<>(toOctetStreamBytes)));
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
        final Map<String, ? extends ResponseContentTypeDefinition<?>> content) {
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
      checkState(
          requestBodyTypes.isEmpty() || requestBodyTypes.containsKey(defaultRequestType),
          "Default request body type is not a supported request type");

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
          defaultResponseType,
          responses,
          defaultRequestType,
          requestBodyTypes,
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
