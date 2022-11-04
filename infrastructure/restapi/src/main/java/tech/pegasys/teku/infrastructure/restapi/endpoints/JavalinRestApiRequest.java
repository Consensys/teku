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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Header;
import io.javalin.http.sse.SseClient;
import io.javalin.http.sse.SseHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;

public class JavalinRestApiRequest implements RestApiRequest {
  private static final Logger LOG = LogManager.getLogger();
  private final Context context;
  private final EndpointMetadata metadata;
  private final Map<String, String> pathParamMap;
  private final Map<String, List<String>> queryParamMap;

  @Override
  @SuppressWarnings({"TypeParameterUnusedInFormals"})
  public <T> T getRequestBody() throws JsonProcessingException {
    return metadata.getRequestBody(
        context.bodyInputStream(), Optional.ofNullable(context.header("Content-Type")));
  }

  public JavalinRestApiRequest(final Context context, final EndpointMetadata metadata) {
    this.context = context;
    this.metadata = metadata;
    this.pathParamMap = context.pathParamMap();
    this.queryParamMap = context.queryParamMap();
  }

  @Override
  public void respondOk(final Object response) throws JsonProcessingException {
    respond(SC_OK, response, getResponseOutputStream());
  }

  @Override
  public void respondAsync(final SafeFuture<AsyncApiResponse> futureResponse) {
    context.future(
        () ->
            futureResponse
                .thenApply(
                    result -> {
                      try {
                        respond(
                            result.getResponseCode(),
                            result.getResponseBody(),
                            getResponseOutputStream());
                      } catch (JsonProcessingException e) {
                        LOG.trace("Failed to generate API response", e);
                        context.status(SC_INTERNAL_SERVER_ERROR);
                      }
                      return Bytes.EMPTY.toArrayUnsafe();
                    })
                .thenApply(ByteArrayInputStream::new));
  }

  @Override
  public void respondOk(final Object response, final CacheLength cacheLength)
      throws JsonProcessingException {
    context.header(Header.CACHE_CONTROL, cacheLength.getHttpHeaderValue());
    respond(SC_OK, response, getResponseOutputStream());
  }

  @Override
  public void respondError(final int statusCode, final String message)
      throws JsonProcessingException {
    respond(statusCode, new HttpErrorResponse(statusCode, message), getResponseOutputStream());
  }

  private OutputStream getResponseOutputStream() {
    try {
      return context.res().getOutputStream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void respond(
      final int statusCode, final Optional<Object> response, final OutputStream out)
      throws JsonProcessingException {
    context.status(statusCode);
    if (response.isPresent()) {
      respondImpl(statusCode, response.get(), out);
    }
  }

  private void respond(final int statusCode, final Object response, final OutputStream out)
      throws JsonProcessingException {
    context.status(statusCode);
    respondImpl(statusCode, response, out);
  }

  private void respondImpl(final int statusCode, final Object response, final OutputStream out)
      throws JsonProcessingException {
    final ResponseMetadata responseMetadata =
        metadata.createResponseMetadata(
            statusCode, Optional.ofNullable(context.header(HEADER_ACCEPT)), response);
    context.contentType(responseMetadata.getContentType());
    responseMetadata.getAdditionalHeaders().forEach(context::header);
    metadata.serialize(statusCode, responseMetadata.getContentType(), response, out);
  }

  /** This is only used when intending to return status code without a response body */
  @Override
  public void respondWithCode(final int statusCode) {
    checkState(
        metadata.isNoContentResponse(statusCode),
        "Content required for status code %s but not provided",
        statusCode);
    respondWithUndocumentedCode(statusCode);
  }
  /** This is only used when intending to return status code without a response body */
  @Override
  public void respondWithUndocumentedCode(final int statusCode) {
    context.status(statusCode);
  }

  @Override
  public void respondWithCode(final int statusCode, final CacheLength cacheLength) {
    context.header(Header.CACHE_CONTROL, cacheLength.getHttpHeaderValue());
    context.status(statusCode);
  }

  @Override
  public <T> T getPathParameter(final ParameterMetadata<T> parameterMetadata) {
    return parameterMetadata
        .getType()
        .deserializeFromString(pathParamMap.get(parameterMetadata.getName()));
  }

  @Override
  public String getResponseContentType(final int statusCode) {
    return metadata.getContentType(statusCode, Optional.ofNullable(context.header(HEADER_ACCEPT)));
  }

  @Override
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

  @Override
  public <T> T getQueryParameter(final ParameterMetadata<T> parameterMetadata) {
    return parameterMetadata
        .getType()
        .deserializeFromString(
            SingleQueryParameterUtils.validateQueryParameter(
                queryParamMap, parameterMetadata.getName()));
  }

  @Override
  public <T> List<T> getQueryParameterList(final ParameterMetadata<T> parameterMetadata) {
    if (!queryParamMap.containsKey(parameterMetadata.getName())) {
      return List.of();
    }

    final List<String> paramList =
        ListQueryParameterUtils.getParameterAsStringList(
            queryParamMap, parameterMetadata.getName());
    return paramList.stream()
        .map(item -> parameterMetadata.getType().deserializeFromString(item))
        .collect(Collectors.toList());
  }

  @Override
  public void header(String name, String value) {
    context.header(name, value);
  }

  @Override
  public void startEventStream(Consumer<SseClient> clientConsumer) {
    SseHandler sseHandler = new SseHandler(clientConsumer);
    sseHandler.handle(context);
  }
}
