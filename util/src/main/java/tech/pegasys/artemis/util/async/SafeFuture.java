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
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SafeFuture<T> extends CompletableFuture<T> {
  private static final Logger LOG = LogManager.getLogger();

  public static SafeFuture<Void> COMPLETE = SafeFuture.completedFuture(null);

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

  public static <U> SafeFuture<U> of(final Supplier<CompletionStage<U>> futureSupplier) {
    try {
      return SafeFuture.of(futureSupplier.get());
    } catch (Throwable e) {
      return SafeFuture.failedFuture(e);
    }
  }

  public static <U> SafeFuture<U> of(final ExceptionThrowingSupplier<U> supplier) {
    try {
      return SafeFuture.completedFuture(supplier.get());
    } catch (final Throwable e) {
      return SafeFuture.failedFuture(e);
    }
  }

  public static <U> SafeFuture<U> ofComposed(
      final ExceptionThrowingSupplier<CompletionStage<U>> futureSupplier) {
    try {
      return SafeFuture.of(futureSupplier.get());
    } catch (final Throwable e) {
      return SafeFuture.failedFuture(e);
    }
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

  public static SafeFuture<Void> fromRunnable(final Runnable action) {
    try {
      action.run();
      return SafeFuture.COMPLETE;
    } catch (Throwable t) {
      return SafeFuture.failedFuture(t);
    }
  }

  public static <U> SafeFuture<Void> allOf(final SafeFuture<?>... futures) {
    return of(CompletableFuture.allOf(futures));
  }

  /**
   * Returns a new SafeFuture that is completed when all of the given SafeFutures complete
   * successfully or completes exceptionally immediately when any of the SafeFutures complete
   * exceptionally. The results, if any, of the given SafeFutures are not reflected in the returned
   * SafeFuture, but may be obtained by inspecting them individually. If no SafeFutures are
   * provided, returns a SafeFuture completed with the value {@code null}.
   *
   * <p>Among the applications of this method is to await completion of a set of independent
   * SafeFutures before continuing a program, as in: {@code SafeFuture.allOf(c1, c2, c3).join();}.
   *
   * @param futures the SafeFutures
   * @return a new SafeFuture that is completed when all of the given SafeFutures complete
   * @throws NullPointerException if the array or any of its elements are {@code null}
   */
  public static <U> SafeFuture<Void> allOfFailFast(final SafeFuture<?>... futures) {
    final SafeFuture<Void> complete = new SafeFuture<>();
    Stream.of(futures).forEach(future -> future.finish(() -> {}, complete::completeExceptionally));
    allOf(futures).propagateTo(complete);
    return complete;
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

  public void propagateTo(final SafeFuture<T> target) {
    propagateResult(this, target);
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
  @SuppressWarnings({"FutureReturnValueIgnored", "MissingOverride"})
  public SafeFuture<T> exceptionallyCompose(
      final Function<Throwable, ? extends CompletionStage<T>> errorHandler) {
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

  /**
   * Returns a new CompletionStage that, when the this stage completes exceptionally, executes the
   * provided Consumer with the exception as the argument. The returned stage will be exceptionally
   * completed with the same exception.
   *
   * <p>This is equivalent to a catch block that performs some action and then rethrows the original
   * exception.
   *
   * @param onError the function to executor when this stage completes exceptionally.
   * @return a new SafeFuture which completes with the same result (successful or exceptionally) as
   *     this stage.
   */
  public SafeFuture<T> catchAndRethrow(final Consumer<Throwable> onError) {
    return exceptionallyCompose(
        error -> {
          onError.accept(error);
          return failedFuture(error);
        });
  }

  public static <U> SafeFuture<U> supplyAsync(final Supplier<U> supplier) {
    return SafeFuture.of(CompletableFuture.supplyAsync(supplier));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> SafeFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
    return (SafeFuture<U>) super.thenApply(fn);
  }

  public <U> SafeFuture<U> thenApplyChecked(final ExceptionThrowingFunction<T, U> function) {
    return thenCompose(
        value -> {
          try {
            final U result = function.apply(value);
            return SafeFuture.completedFuture(result);
          } catch (final Throwable e) {
            return SafeFuture.failedFuture(e);
          }
        });
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
