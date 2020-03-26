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

package tech.pegasys.artemis.beaconrestapi.handlers;

import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.util.Optional;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

public abstract class AbstractHandler implements Handler {

  protected final JsonProvider jsonProvider;

  protected AbstractHandler(final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
  }

  protected <T> void handlePossiblyMissingResult(
      final Context ctx, SafeFuture<Optional<T>> future) {
    handleOptionalResult(ctx, future, SC_NOT_FOUND);
  }

  protected <T> void handlePossiblyMissingResult(
    final Context ctx, SafeFuture<Optional<T>> future, ResultProcessor<T> resultProcessor) {
    handleOptionalResult(ctx, future, resultProcessor, SC_NOT_FOUND);
  }

  protected <T> void handlePossiblyGoneResult(final Context ctx, SafeFuture<Optional<T>> future) {
    handleOptionalResult(ctx, future, SC_GONE);
  }

  protected <T> void handlePossiblyGoneResult(
      final Context ctx, SafeFuture<Optional<T>> future, ResultProcessor<T> resultProcessor) {
    handleOptionalResult(ctx, future, resultProcessor, SC_GONE);
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
      final int missingStatus) {
    ctx.result(
        future.thenApplyChecked(
            result -> {
              if (result.isPresent()) {
                return resultProcessor.process(ctx, result.get()).orElse(null);
              } else {
                ctx.status(missingStatus);
                return null;
              }
            }));
  }

  @FunctionalInterface
  public interface ResultProcessor<T> {
    // Process result, returning an optional serialized response
    Optional<String> process(final Context context, final T result) throws Exception;
  }
}
