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

import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public abstract class AbstractHandler implements Handler {
  protected final JsonProvider jsonProvider;

  protected AbstractHandler(final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
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
    ctx.result(
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
    ctx.result(
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
    ctx.result(
        future.thenApplyChecked(
            result -> {
              if (result.isPresent()) {
                return resultProcessor.process(ctx, result.get()).orElse(null);
              } else {
                ctx.status(missingStatus);
                ctx.result(BadRequest.serialize(jsonProvider, missingStatus, "Not found"));
                return null;
              }
            }));
  }

  protected <T> void handleOptionalSszResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultSszProcessor<T> resultProcessor,
      SszFilenameFromResult<T> resultFilename,
      final int missingStatus) {
    ctx.result(
        future.thenApplyChecked(
            result -> {
              if (result.isPresent()) {
                ctx.contentType("application/octet-stream");
                ctx.header(
                    "Content-Disposition",
                    "filename=\"" + resultFilename.getFilename(result.get()) + "\"");
                return resultProcessor.process(ctx, result.get()).orElse(null);
              } else {
                ctx.status(missingStatus);
                ctx.result(BadRequest.serialize(jsonProvider, missingStatus, "Not found"));
                return null;
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
}
