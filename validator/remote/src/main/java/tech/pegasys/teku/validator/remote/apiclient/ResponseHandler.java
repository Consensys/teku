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

package tech.pegasys.teku.validator.remote.apiclient;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_GATEWAY;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_GATEWAY_TIMEOUT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_TOO_MANY_REQUESTS;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.provider.JsonProvider;

public class ResponseHandler<T> {

  private final Int2ObjectMap<Handler<T>> handlers = new Int2ObjectOpenHashMap<>();
  private final JsonProvider jsonProvider;
  private final Class<T> responseClass;
  private Class<T> badRequestResponseClass;

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
    handler.badRequestResponseClass = badRequestResponseClass;
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
    final String errorMessage = BeaconNodeApiErrorUtils.getErrorMessage(response);
    final String exceptionMessage =
        String.format(
            "Unexpected response from Beacon Node API (url = %s, status = %s, message = %s)",
            request.url(), response.code(), errorMessage);
    throw new RuntimeException(exceptionMessage);
  }

  private Optional<T> noValueHandler(final Request request, final Response response) {
    return Optional.empty();
  }

  private Optional<T> serviceErrorHandler(final Request request, final Response response) {
    final String errorMessage = BeaconNodeApiErrorUtils.getErrorMessage(response);
    throw new RemoteServiceNotAvailableException(
        String.format(
            "Server error from Beacon Node API (url = %s, status = %s, message = %s)",
            request.url(), response.code(), errorMessage));
  }

  private Optional<T> defaultTooManyRequestsHandler(
      final Request request, final Response response) {
    throw new RateLimitedException(request.url().toString());
  }

  private Optional<T> defaultBadRequestHandler(final Request request, final Response response)
      throws IOException {
    if (badRequestResponseClass != null) {
      return parseResponse(response, badRequestResponseClass);
    }
    final String errorMessage = BeaconNodeApiErrorUtils.getErrorMessage(response);
    final String exceptionMessage =
        String.format(
            "Invalid params response from Beacon Node API (url = %s, status = %s, message = %s)",
            request.url(), response.code(), errorMessage);
    throw new IllegalArgumentException(exceptionMessage);
  }

  private Optional<T> defaultOkHandler(final Request request, final Response response)
      throws IOException {
    return parseResponse(response, responseClass);
  }

  private Optional<T> parseResponse(final Response response, final Class<T> responseClass)
      throws IOException {
    final ResponseBody responseBody = response.body();
    if (responseClass != null && responseBody != null) {
      final T responseObj = jsonProvider.jsonToObject(responseBody.string(), responseClass);
      return Optional.of(responseObj);
    } else {
      return Optional.empty();
    }
  }

  public interface Handler<T> {
    Optional<T> handleResponse(Request request, Response response) throws IOException;
  }
}
