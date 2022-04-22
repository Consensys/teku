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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.JSON_CONTENT_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.BadRequest.BAD_REQUEST_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class RestApiRequest {
  private static final Logger LOG = LogManager.getLogger();
  private final Context context;
  private final EndpointMetadata metadata;
  private final Map<String, String> pathParamMap;
  private final Map<String, List<String>> queryParamMap;

  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  public <T> T getRequestBody() throws JsonProcessingException {
    return metadata.getRequestBody(context.body());
  }

  public RestApiRequest(final Context context, final EndpointMetadata metadata) {
    this.context = context;
    this.metadata = metadata;
    this.pathParamMap = context.pathParamMap();
    this.queryParamMap = context.queryParamMap();
  }

  public void respondOk(final Object response) throws JsonProcessingException {
    respond(HttpStatusCodes.SC_OK, JSON_CONTENT_TYPE, response);
  }

  public void respondAsync(final SafeFuture<AsyncApiResponse> futureResponse) {
    context.future(
        futureResponse
            .thenApply(
                result -> {
                  try {
                    return respond(
                        result.getResponseCode(), JSON_CONTENT_TYPE, result.getResponseBody());
                  } catch (JsonProcessingException e) {
                    LOG.trace("Failed to generate API response", e);
                    context.status(SC_INTERNAL_SERVER_ERROR);
                    return Bytes.EMPTY.toArrayUnsafe();
                  }
                })
            .thenApply(ByteArrayInputStream::new));
  }

  public void respondOk(final Object response, final CacheLength cacheLength)
      throws JsonProcessingException {
    context.header(Header.CACHE_CONTROL, cacheLength.getHttpHeaderValue());
    respond(HttpStatusCodes.SC_OK, JSON_CONTENT_TYPE, response);
  }

  public void respondError(final int statusCode, final String message)
      throws JsonProcessingException {
    respond(statusCode, JSON_CONTENT_TYPE, new HttpErrorResponse(statusCode, message));
  }

  private byte[] respond(
      final int statusCode, final String contentType, final Optional<Object> response)
      throws JsonProcessingException {
    context.status(statusCode);
    if (response.isPresent()) {
      return metadata.serialize(statusCode, contentType, response.get());
    }
    return Bytes.EMPTY.toArrayUnsafe();
  }

  private void respond(final int statusCode, final String contentType, final Object response)
      throws JsonProcessingException {
    context.status(statusCode);
    context.contentType(contentType);
    context.result(metadata.serialize(statusCode, contentType, response));
  }

  /** This is only used when intending to return status code without a response body */
  public void respondWithCode(final int statusCode) {
    context.status(statusCode);
  }

  public <T> T getPathParameter(final ParameterMetadata<T> parameterMetadata) {
    return parameterMetadata
        .getType()
        .deserializeFromString(pathParamMap.get(parameterMetadata.getName()));
  }

  public <T> Optional<T> getOptionalQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    if (!queryParamMap.containsKey(parameterMetadata.getName())) {
      return Optional.empty();
    }
    return Optional.of(
        parameterMetadata
            .getType()
            .deserializeFromString(
                SingleQueryParameterUtils.validateQueryParameter(
                    queryParamMap, parameterMetadata.getName())));
  }

  public <T> T getQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    return parameterMetadata
        .getType()
        .deserializeFromString(
            SingleQueryParameterUtils.validateQueryParameter(
                queryParamMap, parameterMetadata.getName()));
  }

  public <T> void handleOptionalResult(
      SafeFuture<Optional<T>> future, ResultProcessor<T> resultProcessor, final int missingStatus) {
    context.future(
        future.thenApplyChecked(
            result -> {
              if (result.isPresent()) {
                return resultProcessor.process(context, result.get()).orElse(null);
              } else {
                context.status(missingStatus);
                return JsonUtil.serialize(
                    new BadRequest(missingStatus, "Not found"), BAD_REQUEST_TYPE);
              }
            }));
  }

  @FunctionalInterface
  public interface ResultProcessor<T> {
    // Process result, returning an optional serialized response
    Optional<String> process(Context context, T result) throws Exception;
  }
}
