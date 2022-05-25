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

package tech.pegasys.teku.validator.remote.typedef;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_GATEWAY;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_GATEWAY_TIMEOUT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_TOO_MANY_REQUESTS;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.validator.remote.apiclient.RateLimitedException;

public class ResponseHandler<TObject> {
  private static final Logger LOG = LogManager.getLogger();
  private final Int2ObjectMap<ResponseHandler.Handler<TObject>> handlers =
      new Int2ObjectOpenHashMap<>();
  private final Optional<DeserializableTypeDefinition<TObject>> maybeTypeDefinition;

  public ResponseHandler(
      final Optional<DeserializableTypeDefinition<TObject>> maybeTypeDefinition) {
    this.maybeTypeDefinition = maybeTypeDefinition;
    withHandler(SC_OK, this::defaultOkHandler);
    withHandler(SC_ACCEPTED, this::noValueHandler);
    withHandler(SC_NO_CONTENT, this::noValueHandler);
    withHandler(SC_BAD_REQUEST, this::defaultBadRequestHandler);
    withHandler(SC_TOO_MANY_REQUESTS, this::defaultTooManyRequestsHandler);
    withHandler(SC_INTERNAL_SERVER_ERROR, this::serviceErrorHandler);
    withHandler(SC_BAD_GATEWAY, this::serviceErrorHandler);
    withHandler(SC_SERVICE_UNAVAILABLE, this::serviceErrorHandler);
    withHandler(SC_GATEWAY_TIMEOUT, this::serviceErrorHandler);
  }

  public ResponseHandler(final DeserializableTypeDefinition<TObject> typeDefinition) {
    this(Optional.of(typeDefinition));
  }

  public ResponseHandler() {
    this(Optional.empty());
  }

  public ResponseHandler<TObject> withHandler(
      final int responseCode, final Handler<TObject> handler) {
    handlers.put(responseCode, handler);
    return this;
  }

  private Optional<TObject> defaultOkHandler(final Request request, final Response response)
      throws IOException {
    if (maybeTypeDefinition.isPresent()) {
      try {
        return Optional.of(JsonUtil.parse(response.body().string(), maybeTypeDefinition.get()));
      } catch (JsonProcessingException e) {
        LOG.debug("Failed to decode response body", e);
      }
    }
    return Optional.empty();
  }

  public Optional<TObject> handleResponse(final Request request, final Response response)
      throws IOException {
    return handlers
        .getOrDefault(response.code(), this::unknownResponseCodeHandler)
        .handleResponse(request, response);
  }

  private Optional<TObject> unknownResponseCodeHandler(
      final Request request, final Response response) {
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

  public interface Handler<TObject> {
    Optional<TObject> handleResponse(Request request, Response response) throws IOException;
  }

  private Optional<TObject> noValueHandler(final Request request, final Response response) {
    return Optional.empty();
  }

  private Optional<TObject> serviceErrorHandler(final Request request, final Response response) {
    throw new RemoteServiceNotAvailableException(
        String.format(
            "Server error from Beacon Node API (url = %s, status = %s)",
            request.url(), response.code()));
  }

  private Optional<TObject> defaultBadRequestHandler(final Request request, final Response response)
      throws IOException {
    final HttpErrorResponse error =
        JsonUtil.parse(response.body().string(), HTTP_ERROR_RESPONSE_TYPE);
    LOG.debug(
        String.format(
            "Server error from Beacon Node API (url = %s, status = %s, message = %s)",
            request.url(), response.code(), error.getMessage()));

    throw new IllegalArgumentException(
        "Invalid params response from Beacon Node API (url = " + request.url() + ")");
  }

  private Optional<TObject> defaultTooManyRequestsHandler(
      final Request request, final Response response) {
    throw new RateLimitedException(request.url().toString());
  }
}
