/*
 * Copyright ConsenSys Software Inc., 2022
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

import static java.util.Objects.requireNonNull;

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
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public class OkHttpRestClient implements RestClient {

  private static final Logger LOG = LogManager.getLogger();

  private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  private final OkHttpClient httpClient;
  private final HttpUrl baseEndpoint;

  public OkHttpRestClient(final OkHttpClient httpClient, final String baseEndpoint) {
    this.httpClient = httpClient;
    this.baseEndpoint = HttpUrl.get(baseEndpoint);
  }

  @Override
  public SafeFuture<Response<Void>> getAsync(final String apiPath) {
    return getAsyncInternal(apiPath, NO_HEADERS, Optional.empty());
  }

  @Override
  public <T> SafeFuture<Response<T>> getAsync(
      final String apiPath,
      final Map<String, String> headers,
      final DeserializableTypeDefinition<T> responseTypeDefinition) {
    return getAsyncInternal(apiPath, headers, Optional.of(responseTypeDefinition));
  }

  @Override
  public <S> SafeFuture<Response<Void>> postAsync(
      final String apiPath,
      final S requestBodyObject,
      final SerializableTypeDefinition<S> requestTypeDefinition) {
    return postAsyncInternal(apiPath, requestBodyObject, requestTypeDefinition, Optional.empty());
  }

  @Override
  public <T, S> SafeFuture<Response<T>> postAsync(
      final String apiPath,
      final S requestBodyObject,
      final SerializableTypeDefinition<S> requestTypeDefinition,
      final DeserializableTypeDefinition<T> responseTypeDefinition) {
    return postAsyncInternal(
        apiPath, requestBodyObject, requestTypeDefinition, Optional.of(responseTypeDefinition));
  }

  private <T> SafeFuture<Response<T>> getAsyncInternal(
      final String apiPath,
      final Map<String, String> headers,
      final Optional<DeserializableTypeDefinition<T>> responseTypeDefinitionMaybe) {
    final Request request = createGetRequest(apiPath, headers);
    return makeAsyncRequest(request, responseTypeDefinitionMaybe);
  }

  private <T, S> SafeFuture<Response<T>> postAsyncInternal(
      final String apiPath,
      final S requestBodyObject,
      final SerializableTypeDefinition<S> requestTypeDefinition,
      final Optional<DeserializableTypeDefinition<T>> responseTypeDefinitionMaybe) {
    final RequestBody requestBody = createRequestBody(requestBodyObject, requestTypeDefinition);
    final Request request = createPostRequest(apiPath, requestBody);
    return makeAsyncRequest(request, responseTypeDefinitionMaybe);
  }

  private Request createGetRequest(final String apiPath, final Map<String, String> headers) {
    final HttpUrl httpUrl = createHttpUrl(apiPath);
    final Request.Builder requestBuilder = new Request.Builder().url(httpUrl);
    headers.forEach(requestBuilder::header);
    return requestBuilder.build();
  }

  private <S> RequestBody createRequestBody(
      final S requestBodyObject, final SerializableTypeDefinition<S> requestTypeDefinition) {
    return new RequestBody() {

      @Override
      public void writeTo(final BufferedSink bufferedSink) throws IOException {
        JsonUtil.serializeToBytesChecked(
            requestBodyObject, requestTypeDefinition, bufferedSink.outputStream());
      }

      @Override
      public MediaType contentType() {
        return JSON_MEDIA_TYPE;
      }
    };
  }

  private Request createPostRequest(final String apiPath, final RequestBody requestBody) {
    final HttpUrl httpUrl = createHttpUrl(apiPath);
    return new Request.Builder().url(httpUrl).post(requestBody).build();
  }

  private HttpUrl createHttpUrl(final String apiPath) {
    final HttpUrl.Builder urlBuilder = baseEndpoint.newBuilder(apiPath);
    return requireNonNull(urlBuilder).build();
  }

  private <T> SafeFuture<Response<T>> makeAsyncRequest(
      final Request request,
      final Optional<DeserializableTypeDefinition<T>> responseTypeDefinitionMaybe) {
    final Call call = httpClient.newCall(request);
    final SafeFuture<Response<T>> futureResponse = new SafeFuture<>();
    final Callback responseCallback =
        new Callback() {
          @Override
          public void onFailure(final Call call, final IOException ex) {
            futureResponse.completeExceptionally(ex);
          }

          @Override
          public void onResponse(final Call call, final okhttp3.Response response) {
            LOG.trace("{} {} {}", request.method(), request.url(), response.code());
            if (!response.isSuccessful()) {
              handleFailure(response, futureResponse);
              return;
            }
            try (final ResponseBody responseBody = response.body()) {
              if (bodyIsEmpty(responseBody) || responseTypeDefinitionMaybe.isEmpty()) {
                futureResponse.complete(Response.withNullPayload());
                return;
              }
              final T payload =
                  JsonUtil.parse(responseBody.byteStream(), responseTypeDefinitionMaybe.get());
              futureResponse.complete(new Response<>(payload));
            } catch (final Throwable ex) {
              futureResponse.completeExceptionally(ex);
            }
          }
        };
    call.enqueue(responseCallback);
    return futureResponse;
  }

  private <T> void handleFailure(
      final okhttp3.Response response, final SafeFuture<Response<T>> futureResponse) {
    try {
      final String errorMessage = getErrorMessageForFailedResponse(response);
      futureResponse.complete(Response.withErrorMessage(errorMessage));
    } catch (final Throwable ex) {
      futureResponse.completeExceptionally(ex);
    }
  }

  private String getErrorMessageForFailedResponse(final okhttp3.Response response)
      throws IOException {
    try (final ResponseBody responseBody = response.body()) {
      if (bodyIsEmpty(responseBody)) {
        return response.code() + ": " + response.message();
      }
      return responseBody.string();
    }
  }

  private boolean bodyIsEmpty(final ResponseBody responseBody) {
    return responseBody == null || responseBody.contentLength() == 0;
  }
}
