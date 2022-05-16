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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.emptyMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public abstract class AbstractTypeDefRequest {
  private static final Logger LOG = LogManager.getLogger();
  private final HttpUrl baseEndpoint;
  private final OkHttpClient httpClient;

  public AbstractTypeDefRequest(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    this.baseEndpoint = baseEndpoint;
    this.httpClient = okHttpClient;
  }

  protected HttpUrl.Builder urlBuilder(
      final ValidatorApiMethod apiMethod, final Map<String, String> urlParams) {
    checkNotNull(apiMethod, "apiMethod not defined");
    final HttpUrl httpUrl = baseEndpoint.resolve(apiMethod.getPath(urlParams));
    checkNotNull(httpUrl, "Could not create endpoint builder from baseEndpoint: " + apiMethod);
    return httpUrl.newBuilder();
  }

  protected Request.Builder requestBuilder() {
    final Request.Builder builder = new Request.Builder();

    if (!baseEndpoint.username().isEmpty()) {
      builder.header(
          "Authorization",
          Credentials.basic(baseEndpoint.encodedUsername(), baseEndpoint.encodedPassword()));
    }
    return builder;
  }

  protected <T> Optional<T> get(
      final ValidatorApiMethod apiMethod, final ResponseHandler<T> responseHandler) {
    return get(apiMethod, emptyMap(), emptyMap(), responseHandler);
  }

  protected <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> queryParams,
      final ResponseHandler<T> responseHandler) {
    return get(apiMethod, emptyMap(), queryParams, responseHandler);
  }

  protected <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> queryParams,
      final ResponseHandler<T> responseHandler) {
    return get(apiMethod, urlParams, queryParams, Map.of(), responseHandler);
  }

  protected <T> Optional<T> get(
      final ValidatorApiMethod apiMethod,
      final Map<String, String> urlParams,
      final Map<String, String> queryParams,
      final Map<String, String> headers,
      final ResponseHandler<T> responseHandler) {
    final HttpUrl.Builder httpUrlBuilder = urlBuilder(apiMethod, urlParams);
    if (queryParams != null && !queryParams.isEmpty()) {
      queryParams.forEach(httpUrlBuilder::addQueryParameter);
    }

    final Request.Builder builder = requestBuilder().url(httpUrlBuilder.build());
    if (headers != null && !headers.isEmpty()) {
      headers.forEach(builder::addHeader);
    }
    return executeCall(builder.build(), responseHandler);
  }

  private <T> Optional<T> executeCall(
      final Request request, final ResponseHandler<T> responseHandler) {
    try (final Response response = httpClient.newCall(request).execute()) {
      LOG.trace("{} {} {}", request.method(), request.url(), response.code());
      return responseHandler.handleResponse(request, response);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Error communicating with Beacon Node API: " + e.getMessage(), e);
    }
  }
}
