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

package tech.pegasys.teku.beaconrestapi.handlers;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.JSON;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.provider.JsonProvider;

public abstract class AbstractHandler implements Handler {
  public static final List<String> SSZ_OR_JSON_CONTENT_TYPES = List.of(OCTET_STREAM, JSON);
  protected final JsonProvider jsonProvider;

  protected AbstractHandler(final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
  }

  protected <T> T parseRequestBody(String json, Class<T> clazz) {
    try {
      return jsonProvider.jsonToObject(json, clazz);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
          "Could read request body to get required data. " + ex.getMessage());
    }
  }

  protected <T> void handleOptionalResult(
      final Context ctx, SafeFuture<Optional<T>> future, final int missingStatus) {
    handleOptionalResult(
        ctx, future, (context, r) -> Optional.of(jsonProvider.objectToJSON(r)), missingStatus);
  }

  protected <T> void handleOptionalResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultProcessor<T> resultProcessor,
      final T emptyResponse) {
    handleOptionalResult(
        ctx, future, resultProcessor, (__, error) -> SafeFuture.failedFuture(error), emptyResponse);
  }

  protected <T> void handleOptionalResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultProcessor<T> resultProcessor,
      ErrorProcessor errorProcessor,
      final T emptyResponse) {
    ctx.future(
        future
            .thenApplyChecked(
                result -> resultProcessor.process(ctx, result.orElse(emptyResponse)).orElse(null))
            .exceptionallyCompose(error -> errorProcessor.handleError(ctx, error)));
  }

  protected <T> void handleOptionalResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultProcessor<T> resultProcessor,
      ErrorProcessor errorProcessor,
      final int missingStatus) {
    ctx.future(
        future
            .thenApplyChecked(
                result -> {
                  if (result.isPresent()) {
                    return resultProcessor.process(ctx, result.get()).orElse(null);
                  } else {
                    ctx.status(missingStatus);
                    return null;
                  }
                })
            .exceptionallyCompose(error -> errorProcessor.handleError(ctx, error)));
  }

  protected <T> void handleOptionalResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultProcessor<T> resultProcessor,
      final int missingStatus) {
    ctx.future(
        future.thenApplyChecked(
            result -> {
              if (result.isPresent()) {
                return resultProcessor.process(ctx, result.get()).orElse(null);
              } else {
                ctx.status(missingStatus);
                return BadRequest.serialize(jsonProvider, missingStatus, "Not found");
              }
            }));
  }

  protected <T> void handleOptionalSszResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultSszProcessor<T> resultProcessor,
      SszFilenameFromResult<T> resultFilename,
      final int missingStatus) {
    ctx.future(
        future.thenApplyChecked(
            result -> {
              if (result.isPresent()) {
                ctx.contentType("application/octet-stream");
                ctx.header(
                    RestApiConstants.HEADER_CONTENT_DISPOSITION,
                    "filename=\"" + resultFilename.getFilename(result.get()) + "\"");
                return resultProcessor.process(ctx, result.get()).orElse(null);
              } else {
                ctx.status(missingStatus);
                return BadRequest.serialize(jsonProvider, missingStatus, "Not found");
              }
            }));
  }

  protected void handlePostDataResult(
      final Context ctx, final SafeFuture<Optional<PostDataFailureResponse>> result) {
    ctx.future(
        result
            .thenApplyChecked(
                errors -> {
                  if (errors.isEmpty()) {
                    ctx.status(SC_OK);
                    return null;
                  }

                  ctx.status(SC_BAD_REQUEST);
                  return jsonProvider.objectToJSON(errors.get());
                })
            .exceptionallyCompose(
                error -> {
                  final Throwable rootCause = Throwables.getRootCause(error);
                  if (rootCause instanceof IllegalArgumentException) {
                    ctx.status(SC_BAD_REQUEST);
                    return SafeFuture.of(
                        () -> BadRequest.badRequest(jsonProvider, rootCause.getMessage()));
                  } else {
                    return failedFuture(error);
                  }
                }));
  }

  @FunctionalInterface
  public interface ResultProcessor<T> {
    // Process result, returning an optional serialized response
    Optional<String> process(Context context, T result) throws Exception;
  }

  @FunctionalInterface
  public interface ResultSszProcessor<T> {
    // Process result, returning an optional Ssz byte stream response
    Optional<ByteArrayInputStream> process(Context context, T result) throws Exception;
  }

  @FunctionalInterface
  public interface SszFilenameFromResult<T> {
    String getFilename(T result) throws Exception;
  }

  @FunctionalInterface
  public interface ErrorProcessor {
    SafeFuture<String> handleError(Context context, Throwable t);
  }

  public static String routeWithBracedParameters(final String route) {
    return route.replaceAll(":([a-z_A-Z]+)", "{$1}");
  }

  public static String getContentType(
      final List<String> types, final Optional<String> maybeContentType) {
    return ContentTypes.getContentType(types, maybeContentType).orElse(JSON);
  }
}
