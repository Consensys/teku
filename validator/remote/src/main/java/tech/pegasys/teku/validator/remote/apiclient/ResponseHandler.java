/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.remote.apiclient;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_GATEWAY;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_GATEWAY_TIMEOUT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_TOO_MANY_REQUESTS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.provider.JsonProvider;

public class ResponseHandler<T> {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Integer, Handler<T>> handlers = new HashMap<>();
  private final JsonProvider jsonProvider;
  private final Class<T> responseClass;
  private Class<T> badReqeustResponseClass;

  public ResponseHandler(final JsonProvider jsonProvider, final Class<T> responseClass) {
    this.jsonProvider = jsonProvider;
    this.responseClass = responseClass;
    withHandler(SC_OK, this::defaultOkHandler);
    withHandler(SC_ACCEPTED, this::noValueHandler);
    withHandler(SC_NO_CONTENT, this::noValueHandler);
    withHandler(SC_SERVICE_UNAVAILABLE, this::serviceErrorHandler);
    withHandler(SC_BAD_GATEWAY, this::serviceErrorHandler);
    withHandler(SC_BAD_REQUEST, this::defaultBadRequestHandler);
    withHandler(SC_GATEWAY_TIMEOUT, this::serviceErrorHandler);
    withHandler(SC_TOO_MANY_REQUESTS, this::defaultTooManyRequestsHandler);
  }

  public static <T> ResponseHandler<T> createForEmptyOkAndContentInBadResponse(
      final JsonProvider jsonProvider, final Class<T> badRequestResponseClass) {
    final ResponseHandler<T> handler = new ResponseHandler<>(jsonProvider, null);
    handler.badReqeustResponseClass = badRequestResponseClass;
    return handler;
  }

  public ResponseHandler<T> withHandler(final int responseCode, final Handler<T> handler) {
    handlers.put(responseCode, handler);
    return this;
  }

  public Optional<T> handleResponse(final Request request, final Response response)
      throws IOException {
    return handlers
        .getOrDefault(response.code(), this::unknownResponseCodeHandler)
        .handleResponse(request, response);
  }

  private Optional<T> unknownResponseCodeHandler(final Request request, final Response response) {
    LOG.debug(
        "Unexpected response from Beacon Node API (url = {}, status = {}, response = {})",
        request.url(),
        response.code(),
        response.body());
    throw new RuntimeException(
        String.format(
            "Unexpected response from Beacon Node API (url = %s, status = %s)",
            request.url(), response.code()));
  }

  private Optional<T> noValueHandler(final Request request, final Response response) {
    return Optional.empty();
  }

  private Optional<T> serviceErrorHandler(final Request request, final Response response) {
    throw new RemoteServiceNotAvailableException(
        String.format(
            "Server error from Beacon Node API (url = %s, status = %s)",
            request.url(), response.code()));
  }

  private Optional<T> defaultTooManyRequestsHandler(
      final Request request, final Response response) {
    throw new RateLimitedException(request.url().toString());
  }

  private Optional<T> defaultBadRequestHandler(final Request request, final Response response)
      throws IOException {
    if (badReqeustResponseClass != null) {
      return parseResponse(response, badReqeustResponseClass);
    }
    LOG.debug(
        "Invalid params response from Beacon Node API (url = {}, response = {})",
        request.url(),
        response.body().string());
    throw new IllegalArgumentException(
        "Invalid params response from Beacon Node API (url = " + request.url() + ")");
  }

  private Optional<T> defaultOkHandler(final Request request, final Response response)
      throws IOException {
    return parseResponse(response, responseClass);
  }

  private Optional<T> parseResponse(final Response response, final Class<T> responseClass)
      throws IOException {
    if (responseClass != null) {
      final T responseObj = jsonProvider.jsonToObject(response.body().string(), responseClass);
      return Optional.of(responseObj);
    } else {
      return Optional.empty();
    }
  }

  public interface Handler<T> {
    Optional<T> handleResponse(Request request, Response response) throws IOException;
  }
}
