/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public class OkHttpRestClient implements RestClient {

  private static final Logger LOG = LogManager.getLogger();

  private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  private static final Map<String, String> EMPTY_QUERY_PARAMS = emptyMap();

  private final OkHttpClient httpClient;
  private final HttpUrl baseEndpoint;

  public OkHttpRestClient(final OkHttpClient httpClient, final String baseEndpoint) {
    this.httpClient = httpClient;
    this.baseEndpoint = HttpUrl.get(baseEndpoint);
  }

  @Override
  public SafeFuture<Response<Void>> getAsync(final String apiPath) {
    return getAsync(apiPath, EMPTY_QUERY_PARAMS, Optional.empty());
  }

  @Override
  public <T> SafeFuture<Response<T>> getAsync(
      final String apiPath, final DeserializableTypeDefinition<T> responseTypeDefinition) {
    return getAsync(apiPath, EMPTY_QUERY_PARAMS, Optional.of(responseTypeDefinition));
  }

  @Override
  public <S> SafeFuture<Response<Void>> postAsync(
      final String apiPath,
      final S requestBodyObject,
      final SerializableTypeDefinition<S> requestTypeDefinition) {
    return postAsync(
        apiPath, EMPTY_QUERY_PARAMS, requestBodyObject, requestTypeDefinition, Optional.empty());
  }

  @Override
  public <T, S> SafeFuture<Response<T>> postAsync(
      final String apiPath,
      final S requestBodyObject,
      final SerializableTypeDefinition<S> requestTypeDefinition,
      final DeserializableTypeDefinition<T> responseTypeDefinition) {
    return postAsync(
        apiPath,
        EMPTY_QUERY_PARAMS,
        requestBodyObject,
        requestTypeDefinition,
        Optional.of(responseTypeDefinition));
  }

  private <T> SafeFuture<Response<T>> getAsync(
      final String apiPath,
      final Map<String, String> queryParams,
      final Optional<DeserializableTypeDefinition<T>> responseTypeDefinitionMaybe) {
    final Request request = createGetRequest(apiPath, queryParams);
    return makeAsyncRequest(request, responseTypeDefinitionMaybe);
  }

  private <T, S> SafeFuture<Response<T>> postAsync(
      final String apiPath,
      final Map<String, String> queryParams,
      final S requestBodyObject,
      final SerializableTypeDefinition<S> requestTypeDefinition,
      final Optional<DeserializableTypeDefinition<T>> responseTypeDefinitionMaybe) {
    final RequestBody requestBody = createRequestBody(requestBodyObject, requestTypeDefinition);
    final Request request = createPostRequest(apiPath, queryParams, requestBody);
    return makeAsyncRequest(request, responseTypeDefinitionMaybe);
  }

  private Request createGetRequest(final String apiPath, final Map<String, String> queryParams) {
    final HttpUrl httpUrl = createHttpUrl(apiPath, queryParams);
    return new Request.Builder().url(httpUrl).build();
  }

  private <S> RequestBody createRequestBody(
      final S requestBodyObject, final SerializableTypeDefinition<S> requestTypeDefinition) {
    return new RequestBody() {

      @Override
      public void writeTo(@NotNull BufferedSink bufferedSink) throws JsonProcessingException {
        JsonUtil.serializeToBytes(
            requestBodyObject, requestTypeDefinition, bufferedSink.outputStream());
      }

      @Nullable
      @Override
      public MediaType contentType() {
        return JSON_MEDIA_TYPE;
      }
    };
  }

  private Request createPostRequest(
      final String apiPath, final Map<String, String> queryParams, RequestBody requestBody) {
    final HttpUrl httpUrl = createHttpUrl(apiPath, queryParams);
    return new Request.Builder().url(httpUrl).post(requestBody).build();
  }

  private HttpUrl createHttpUrl(final String apiPath, final Map<String, String> queryParams) {
    final HttpUrl.Builder urlBuilder = baseEndpoint.newBuilder(apiPath);
    queryParams.forEach(requireNonNull(urlBuilder)::addQueryParameter);
    return urlBuilder.build();
  }

  private <T> SafeFuture<Response<T>> makeAsyncRequest(
      final Request request,
      final Optional<DeserializableTypeDefinition<T>> responseTypeDefinitionMaybe) {
    final SafeFuture<Response<T>> futureResponse = new SafeFuture<>();
    final Callback responseCallback =
        new Callback() {
          @Override
          public void onFailure(@NotNull final Call call, @NotNull final IOException ex) {
            futureResponse.completeExceptionally(ex);
          }

          @Override
          public void onResponse(
              @NotNull final Call call, @NotNull final okhttp3.Response response) {
            HttpUrl requestUrl = response.request().url();
            LOG.trace("{} {} {}", response.request().method(), requestUrl, response.code());
            if (!response.isSuccessful()) {
              handleFailure(response, futureResponse);
              return;
            }
            final ResponseBody responseBody = response.body();
            if (responseBody == null || responseTypeDefinitionMaybe.isEmpty()) {
              futureResponse.complete(Response.withNullPayload());
              return;
            }
            try {
              final T payload =
                  JsonUtil.parse(responseBody.byteStream(), responseTypeDefinitionMaybe.get());
              futureResponse.complete(new Response<>(payload));
            } catch (Throwable ex) {
              futureResponse.completeExceptionally(ex);
            }
          }
        };
    httpClient.newCall(request).enqueue(responseCallback);
    return futureResponse;
  }

  private <T> void handleFailure(
      final okhttp3.Response response, final SafeFuture<Response<T>> futureResponse) {
    try {
      final String errorMessage = getErrorMessageForFailedResponse(response);
      futureResponse.complete(Response.withErrorMessage(errorMessage));
    } catch (Throwable ex) {
      futureResponse.completeExceptionally(ex);
    }
  }

  private String getErrorMessageForFailedResponse(final okhttp3.Response response)
      throws IOException {
    final String errorCodeAndStatusMessage = response.code() + ": " + response.message();
    final ResponseBody responseBody = response.body();
    if (responseBody == null) {
      return errorCodeAndStatusMessage;
    }
    final String failureBody = responseBody.string();
    if (Strings.nullToEmpty(failureBody).isBlank()) {
      return errorCodeAndStatusMessage;
    } else {
      return failureBody;
    }
  }
}
