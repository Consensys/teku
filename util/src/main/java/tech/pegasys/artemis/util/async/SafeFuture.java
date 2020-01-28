/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.util.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SafeFuture<T> extends CompletableFuture<T> {
  private static final Logger LOG = LogManager.getLogger();

  public static void reportExceptions(final CompletionStage<?> future) {
    future.exceptionally(
        error -> {
          LOG.error("Unhandled exception", error);
          return null;
        });
  }

  public static <T, X extends CompletionStage<?>> Consumer<T> reportExceptions(
      final Function<T, X> action) {
    return value -> reportExceptions(action.apply(value));
  }

  public static <U> SafeFuture<U> completedFuture(U value) {
    SafeFuture<U> future = new SafeFuture<>();
    future.complete(value);
    return future;
  }

  public static <U> SafeFuture<U> failedFuture(Throwable ex) {
    SafeFuture<U> future = new SafeFuture<>();
    future.completeExceptionally(ex);
    return future;
  }

  public static <U> SafeFuture<U> of(final CompletionStage<U> stage) {
    if (stage instanceof SafeFuture) {
      return (SafeFuture<U>) stage;
    }
    final SafeFuture<U> safeFuture = new SafeFuture<>();
    propagateResult(stage, safeFuture);
    return safeFuture;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  static <U> void propagateResult(final CompletionStage<U> stage, final SafeFuture<U> safeFuture) {
    stage.whenComplete(
        (result, error) -> {
          if (error != null) {
            safeFuture.completeExceptionally(error);
          } else {
            safeFuture.complete(result);
          }
        });
  }

  public static <U> SafeFuture<Void> allOf(final SafeFuture<?>... futures) {
    return of(CompletableFuture.allOf(futures));
  }

  public static SafeFuture<Object> anyOf(final SafeFuture<?>... futures) {
    return of(CompletableFuture.anyOf(futures));
  }

  @Override
  public <U> SafeFuture<U> newIncompleteFuture() {
    return new SafeFuture<>();
  }

  public void reportExceptions() {
    reportExceptions(this);
  }

  public void finish(final Runnable onSuccess) {
    finish(complete -> onSuccess.run());
  }

  public void finish(final Consumer<T> onSuccess) {
    reportExceptions(thenAccept(onSuccess));
  }

  public void finish(final Runnable onSuccess, final Consumer<Throwable> onError) {
    finish(result -> onSuccess.run(), onError);
  }

  /**
   * Run final logic on success or error
   *
   * @param onFinished Task to run when future completes successfully or exceptionally
   */
  public void always(final Runnable onFinished) {
    finish(res -> onFinished.run(), err -> onFinished.run());
  }

  public void finish(final Consumer<T> onSuccess, final Consumer<Throwable> onError) {
    handle(
            (result, error) -> {
              if (error != null) {
                onError.accept(error);
              } else {
                onSuccess.accept(result);
              }
              return null;
            })
        .reportExceptions();
  }

  /**
   * Returns a new CompletionStage that, when the provided stage completes exceptionally, is
   * executed with the provided stage's exception as the argument to the supplied function.
   * Otherwise the returned stage completes successfully with the same value as the provided stage.
   *
   * <p>This is the exceptional equivalent to {@link CompletionStage#thenCompose(Function)}
   *
   * @param errorHandler the function returning a new CompletionStage
   * @return the SafeFuture
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<T> exceptionallyCompose(
      final Function<Throwable, CompletionStage<T>> errorHandler) {
    final SafeFuture<T> result = new SafeFuture<>();
    whenComplete(
        (value, error) -> {
          try {
            final CompletionStage<T> nextStep =
                error != null ? errorHandler.apply(error) : completedFuture(value);
            propagateResult(nextStep, result);
          } catch (final Throwable t) {
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> SafeFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
    return (SafeFuture<U>) super.thenApply(fn);
  }

  @Override
  public SafeFuture<Void> thenRun(final Runnable action) {
    return (SafeFuture<Void>) super.thenRun(action);
  }

  @Override
  public SafeFuture<Void> thenAccept(final Consumer<? super T> action) {
    return (SafeFuture<Void>) super.thenAccept(action);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, V> SafeFuture<V> thenCombine(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn) {
    return (SafeFuture<V>) super.thenCombine(other, fn);
  }

  @Override
  public <U> SafeFuture<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
    return (SafeFuture<U>) super.thenCompose(fn);
  }

  @Override
  public SafeFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
    return (SafeFuture<T>) super.exceptionally(fn);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> SafeFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
    return (SafeFuture<U>) super.handle(fn);
  }

  @Override
  public SafeFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
    return (SafeFuture<T>) super.whenComplete(action);
  }

  @Override
  public SafeFuture<T> orTimeout(final long timeout, final TimeUnit unit) {
    return (SafeFuture<T>) super.orTimeout(timeout, unit);
  }
}
