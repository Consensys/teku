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

package tech.pegasys.teku.infrastructure.restapi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiResponse;
import tech.pegasys.teku.infrastructure.restapi.types.TypeDefinition;

public abstract class ApiDefinition<TBody> implements Handler {

  private final ApiMetaData<TBody> metaData;

  protected ApiDefinition(final ApiMetaData<TBody> metaData) {
    this.metaData = metaData;
  }

  protected static ApiMetaDataBuilder<Void> get(final String path) {
    return new ApiMetaDataBuilder<Void>().method(HandlerType.GET).path(path);
  }

  public String getPath() {
    return metaData.path;
  }

  public List<ParameterDefinition<?>> getParameters() {
    return metaData.parameters;
  }

  public Optional<BodyContentDefinition<TBody>> getBodyContent() {
    return metaData.bodyContent;
  }

  public Int2ObjectMap<OpenApiResponse> getResponses() {
    return metaData.responses;
  }

  public HandlerType getHandlerType() {
    return metaData.handlerType;
  }

  @Override
  public final void handle(final Context ctx) throws Exception {
    // TODO: Handle it...

  }

  public abstract void handleRequest() throws Exception;

  public void serializeOpenApi(final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {
    gen.writeObjectFieldStart(metaData.path);
    gen.writeObjectFieldStart(metaData.handlerType.name().toLowerCase(Locale.ROOT));

    gen.writeArrayFieldStart("tags");
    for (String tag : metaData.tags) {
      gen.writeString(tag);
    }
    gen.writeEndArray();

    gen.writeStringField("summary", metaData.summary);
    gen.writeStringField("description", metaData.description);
    gen.writeStringField("operationId", metaData.operationId);

    if (!metaData.parameters.isEmpty()) {
      gen.writeArrayFieldStart("parameters");
      for (ParameterDefinition<?> parameter : metaData.parameters) {
        gen.writeStartObject();
        gen.writeStringField("name", parameter.getName());
        gen.writeStringField("in", parameter.inPath ? "path" : "query");
        gen.writeStringField("description", parameter.getDescription());
        gen.writeBooleanField("required", parameter.required);
        gen.writeFieldName("schema");
        parameter.type.serializeOpenApiTypeOrReference(gen, serializers);
        gen.writeEndObject();
      }
      gen.writeEndArray();
    }

    gen.writeObjectFieldStart("responses");
    for (Entry<OpenApiResponse> entry : metaData.responses.int2ObjectEntrySet()) {
      final OpenApiResponse response = entry.getValue();
      gen.writeObjectFieldStart(Integer.toString(entry.getIntKey()));
      gen.writeStringField("description", response.getDescription());
      if (response.getContent().isPresent()) {
        gen.writeObjectFieldStart("content");
        for (Map.Entry<String, TypeDefinition<?>> contentEntry :
            response.getContent().get().entrySet()) {
          gen.writeObjectFieldStart(contentEntry.getKey());
          gen.writeFieldName("schema");
          contentEntry.getValue().serializeOpenApiTypeOrReference(gen, serializers);
          gen.writeEndObject();
        }
        gen.writeEndObject();
      }
      gen.writeEndObject();
    }
    gen.writeEndObject();

    gen.writeEndObject();
    gen.writeEndObject();
  }

  public static class ApiMetaData<TBody> {
    private final HandlerType handlerType;
    private final String path;
    private final String operationId;
    private final String summary;
    private final String description;
    private final List<ParameterDefinition<?>> parameters;
    private final Optional<BodyContentDefinition<TBody>> bodyContent;
    private final Int2ObjectMap<OpenApiResponse> responses;
    private final List<String> tags;

    public ApiMetaData(
        final HandlerType handlerType,
        final String path,
        final String summary,
        final String description,
        final List<ParameterDefinition<?>> parameters,
        final Optional<BodyContentDefinition<TBody>> bodyContent,
        final Int2ObjectMap<OpenApiResponse> responses,
        final List<String> tags) {
      this.handlerType = handlerType;
      this.path = path;
      this.summary = summary;
      this.description = description;
      this.parameters = parameters;
      this.bodyContent = bodyContent;
      this.responses = responses;
      this.tags = tags;

      // TODO: Should capitalise the first letter after each / in path
      this.operationId = handlerType.name().toLowerCase(Locale.ROOT) + path.replace("/", "");
    }
  }

  public static class ApiMetaDataBuilder<TBody> {
    private HandlerType method;
    private String path;
    private String summary;
    private String description;
    private Optional<BodyContentDefinition<TBody>> bodyContent = Optional.empty();
    private final List<ParameterDefinition<?>> parameters = new ArrayList<>();
    private final Int2ObjectMap<OpenApiResponse> responses = new Int2ObjectOpenHashMap<>();
    private final List<String> tags = new ArrayList<>();

    public ApiMetaDataBuilder<TBody> method(final HandlerType method) {
      this.method = method;
      return this;
    }

    public ApiMetaDataBuilder<TBody> path(final String path) {
      this.path = path;
      return this;
    }

    public ApiMetaDataBuilder<TBody> summary(final String summary) {
      this.summary = summary;
      return this;
    }

    public ApiMetaDataBuilder<TBody> description(final String description) {
      this.description = description;
      return this;
    }

    public ApiMetaDataBuilder<TBody> body(
        final TypeDefinition<TBody> contentType, final boolean required, final String description) {
      this.bodyContent =
          Optional.of(new BodyContentDefinition<>(description, required, contentType));
      return this;
    }

    public ApiMetaDataBuilder<TBody> pathParam(
        final String name, final String description, final TypeDefinition<?> type) {
      this.parameters.add(new ParameterDefinition<>(name, description, true, true, type));
      return this;
    }

    public ApiMetaDataBuilder<TBody> queryParam(
        final String name,
        final String description,
        final TypeDefinition<?> type,
        final boolean required) {
      this.parameters.add(new ParameterDefinition<>(name, description, false, required, type));
      return this;
    }

    public ApiMetaDataBuilder<TBody> response(final int responseCode, final String description) {
      this.responses.put(responseCode, new OpenApiResponse(description, Optional.empty()));
      return this;
    }

    public ApiMetaDataBuilder<TBody> response(
        final int responseCode, final String description, final TypeDefinition<?> content) {
      this.responses.put(
          responseCode,
          new OpenApiResponse(description, Optional.of(Map.of("application/json", content))));
      return this;
    }

    public ApiMetaDataBuilder<TBody> response(
        final int responseCode,
        final String description,
        final Map<String, TypeDefinition<?>> content) {
      this.responses.put(responseCode, new OpenApiResponse(description, Optional.of(content)));
      return this;
    }

    public ApiMetaDataBuilder<TBody> tags(final String... tags) {
      this.tags.addAll(List.of(tags));
      return this;
    }

    public ApiMetaData<TBody> build() {
      return new ApiMetaData<>(
          method, path, summary, description, parameters, bodyContent, responses, tags);
    }
  }

  public static class BodyContentDefinition<T> {
    private final String description;
    private final boolean required;
    private final TypeDefinition<T> content;

    public BodyContentDefinition(
        final String description, final boolean required, final TypeDefinition<T> content) {
      this.description = description;
      this.required = required;
      this.content = content;
    }

    public String getDescription() {
      return description;
    }

    public boolean isRequired() {
      return required;
    }

    public TypeDefinition<T> getContent() {
      return content;
    }
  }

  public static class ParameterDefinition<T> {
    private String name;
    private String description;
    private boolean inPath;
    private boolean required;
    private TypeDefinition<T> type;

    public ParameterDefinition(
        final String name,
        final String description,
        final boolean inPath,
        final boolean required,
        final TypeDefinition<T> type) {
      this.name = name;
      this.description = description;
      this.inPath = inPath;
      this.required = required;
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public boolean isInPath() {
      return inPath;
    }

    public boolean isRequired() {
      return required;
    }

    public TypeDefinition<T> getType() {
      return type;
    }
  }
}
