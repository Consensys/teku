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

package tech.pegasys.teku.infrastructure.async;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SafeFuture<T> extends CompletableFuture<T> {

  public static SafeFuture<Void> COMPLETE = SafeFuture.completedFuture(null);

  public static void reportExceptions(final CompletionStage<?> future) {
    future.exceptionally(
        error -> {
          final Thread currentThread = Thread.currentThread();
          currentThread.getUncaughtExceptionHandler().uncaughtException(currentThread, error);
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

  public static <U> SafeFuture<U> of(final ExceptionThrowingFutureSupplier<U> futureSupplier) {
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

  /**
   * Creates a completed {@link SafeFuture} instance if none of the supplied interruptors are
   * completed, else creates an exceptionally completed {@link SafeFuture} instance
   *
   * @see #orInterrupt(Interruptor...)
   */
  public static SafeFuture<Void> notInterrupted(Interruptor... interruptors) {
    SafeFuture<Void> delayedFuture = new SafeFuture<>();
    SafeFuture<Void> ret = delayedFuture.orInterrupt(interruptors);
    delayedFuture.complete(null);
    return ret;
  }

  /**
   * Creates an {@link Interruptor} instance from the interrupting future and exception supplier for
   * the case if interruption is triggered.
   *
   * <p>The key feature of {@link Interruptor} and {@link #orInterrupt(Interruptor...)} method is
   * that {@code interruptFuture} doesn't hold the reference to dependent futures after they
   * complete. It's desired to consider this for long living interrupting futures to avoid memory
   * leaks
   *
   * @param interruptFuture the future which triggers interruption when completes (normally or
   *     exceptionally)
   * @param exceptionSupplier creates a desired exception if interruption is triggered
   * @see #notInterrupted(Interruptor...)
   * @see #orInterrupt(Interruptor...)
   */
  public static Interruptor createInterruptor(
      CompletableFuture<?> interruptFuture, Supplier<Exception> exceptionSupplier) {
    return new Interruptor(interruptFuture, exceptionSupplier);
  }

  /**
   * Repeatedly run the loop until it returns false or completes exceptionally
   *
   * @param loopBody A supplier for generating futures to be run in succession
   * @return A future that will complete when looping terminates
   */
  public static SafeFuture<Void> asyncDoWhile(ExceptionThrowingFutureSupplier<Boolean> loopBody) {
    // Loop while futures complete immediately in order to avoid stack overflow due to recursion
    SafeFuture<Boolean> loopFuture = SafeFuture.of(loopBody);
    while (loopFuture.isCompletedNormally()) {
      if (!loopFuture.join()) {
        // Break if the result is false
        break;
      }
      loopFuture = SafeFuture.of(loopBody);
    }

    return loopFuture.thenCompose(res -> res ? asyncDoWhile(loopBody) : SafeFuture.COMPLETE);
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

  public static SafeFuture<Void> fromRunnable(final ExceptionThrowingRunnable action) {
    try {
      action.run();
      return SafeFuture.COMPLETE;
    } catch (Throwable t) {
      return SafeFuture.failedFuture(t);
    }
  }

  public static SafeFuture<Void> allOf(final SafeFuture<?>... futures) {
    return of(CompletableFuture.allOf(futures))
        .catchAndRethrow(completionException -> addSuppressedErrors(completionException, futures));
  }

  /**
   * Adds the {@link Throwable} from each future as a suppressed exception to completionException
   * unless it is already set as the cause.
   *
   * <p>This ensures that when futures are combined with {@link #allOf(SafeFuture[])} that all
   * failures are reported, not just the first one.
   *
   * @param completionException the exception reported by {@link
   *     CompletableFuture#allOf(CompletableFuture[])}
   * @param futures the futures passed to allOf
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  public static void addSuppressedErrors(
      final Throwable completionException, final SafeFuture<?>[] futures) {
    Stream.of(futures)
        .forEach(
            future ->
                future.exceptionally(
                    error -> {
                      if (completionException.getCause() != error) {
                        completionException.addSuppressed(error);
                      }
                      return null;
                    }));
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

  public SafeFuture<Void> toVoid() {
    return thenAccept(__ -> {});
  }

  public boolean isCompletedNormally() {
    return isDone() && !isCompletedExceptionally() && !isCancelled();
  }

  @Override
  public <U> SafeFuture<U> newIncompleteFuture() {
    return new SafeFuture<>();
  }

  public void reportExceptions() {
    reportExceptions(this);
  }

  public void finish(final Runnable onSuccess, final Consumer<Throwable> onError) {
    finish(result -> onSuccess.run(), onError);
  }

  public void propagateTo(final SafeFuture<T> target) {
    propagateResult(this, target);
  }

  public void propagateToAsync(final SafeFuture<T> target, final AsyncRunner asyncRunner) {
    finish(
        result -> asyncRunner.runAsync(() -> target.complete(result)).reportExceptions(),
        error ->
            asyncRunner.runAsync(() -> target.completeExceptionally(error)).reportExceptions());
  }

  /**
   * Completes the {@code target} exceptionally if and only if this future is completed
   * exceptionally
   */
  public void propagateExceptionTo(final SafeFuture<?> target) {
    finish(() -> {}, target::completeExceptionally);
  }

  /**
   * Run final logic on success or error
   *
   * @param onFinished Task to run when future completes successfully or exceptionally
   */
  public void always(final Runnable onFinished) {
    finish(res -> onFinished.run(), err -> onFinished.run());
  }

  public SafeFuture<T> alwaysRun(final Runnable action) {
    return exceptionallyCompose(
            error -> {
              action.run();
              return failedFuture(error);
            })
        .thenPeek(value -> action.run());
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

  public void finish(final Consumer<Throwable> onError) {
    handle(
            (result, error) -> {
              if (error != null) {
                onError.accept(error);
              }
              return null;
            })
        .reportExceptions();
  }

  public void finishAsync(final Consumer<Throwable> onError, final Executor executor) {
    finishAsync(__ -> {}, onError, executor);
  }

  public void finishAsync(
      final Runnable onSuccess, final Consumer<Throwable> onError, final Executor executor) {
    finishAsync(__ -> onSuccess.run(), onError, executor);
  }

  public void finishAsync(
      final Consumer<T> onSuccess, final Consumer<Throwable> onError, final Executor executor) {
    handleAsync(
            (result, error) -> {
              if (error != null) {
                onError.accept(error);
              } else {
                onSuccess.accept(result);
              }
              return null;
            },
            executor)
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

  /** Shortcut to process the value when complete and return the same future */
  public SafeFuture<T> thenPeek(Consumer<T> fn) {
    return thenApply(
        v -> {
          fn.accept(v);
          return v;
        });
  }

  @Override
  public SafeFuture<Void> thenRun(final Runnable action) {
    return (SafeFuture<Void>) super.thenRun(action);
  }

  @Override
  public SafeFuture<Void> thenRunAsync(final Runnable action, final Executor executor) {
    return (SafeFuture<Void>) super.thenRunAsync(action, executor);
  }

  @Override
  public SafeFuture<Void> thenAccept(final Consumer<? super T> action) {
    return (SafeFuture<Void>) super.thenAccept(action);
  }

  @Override
  public SafeFuture<Void> thenAcceptAsync(
      final Consumer<? super T> action, final Executor executor) {
    return (SafeFuture<Void>) super.thenAcceptAsync(action, executor);
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
  public <U> SafeFuture<U> thenComposeAsync(
      final Function<? super T, ? extends CompletionStage<U>> fn, final Executor executor) {
    return (SafeFuture<U>) super.thenComposeAsync(fn, executor);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, V> SafeFuture<V> thenCombineAsync(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn,
      final Executor executor) {
    return (SafeFuture<V>) super.thenCombineAsync(other, fn, executor);
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

  @SuppressWarnings("unchecked")
  @Override
  public <U> SafeFuture<U> handleAsync(
      final BiFunction<? super T, Throwable, ? extends U> fn, final Executor executor) {
    return (SafeFuture<U>) super.handleAsync(fn, executor);
  }

  /**
   * Returns a new CompletionStage that, when this stage completes either normally or exceptionally,
   * is executed with this stage's result and exception as arguments to the supplied function.
   *
   * <p>When this stage is complete, the given function is invoked with the result (or {@code null}
   * if none) and the exception (or {@code null} if none) returning another `CompletionStage`. When
   * that stage completes, the `SafeFuture` returned by this method is completed with the same value
   * or exception.
   *
   * @param fn the function to use to compute another CompletionStage
   * @param <U> the function's return type
   * @return the new SafeFuture
   */
  @SuppressWarnings({"FutureReturnValueIgnored"})
  public <U> SafeFuture<U> handleComposed(
      final BiFunction<? super T, Throwable, CompletionStage<U>> fn) {
    final SafeFuture<U> result = new SafeFuture<>();
    whenComplete(
        (value, error) -> {
          try {
            propagateResult(fn.apply(value, error), result);
          } catch (final Throwable t) {
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  @Override
  public SafeFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
    return (SafeFuture<T>) super.whenComplete(action);
  }

  @Override
  public SafeFuture<T> orTimeout(final long timeout, final TimeUnit unit) {
    return (SafeFuture<T>) super.orTimeout(timeout, unit);
  }

  /**
   * Returns the future which completes with the same result or exception The consumer is invoked if
   * this future completes exceptionally
   */
  public SafeFuture<T> whenException(final Consumer<Throwable> action) {
    return (SafeFuture<T>)
        super.whenComplete(
            (r, t) -> {
              if (t != null) {
                action.accept(t);
              }
            });
  }

  /**
   * Returns the future which completes with the same result or exception as this one. The resulting
   * future becomes complete when `waitForStage` completes. If the `waitForStage` completes
   * exceptionally the resulting future also completes exceptionally with the same exception
   */
  public SafeFuture<T> thenWaitFor(Function<T, CompletionStage<?>> waitForStage) {
    return thenCompose(t -> waitForStage.apply(t).thenApply(__ -> t));
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  public final SafeFuture<T> or(SafeFuture<T>... others) {
    SafeFuture<T>[] futures = Arrays.copyOf(others, others.length + 1);
    futures[others.length] = this;
    return anyOf(futures).thenApply(o -> (T) o);
  }

  /**
   * Derives a {@link SafeFuture} which yields the same result as this {@link SafeFuture} if no
   * {@link Interruptor} was triggered before this future is done.
   *
   * <p>If any of supplied {@link Interruptor}s is triggered the returned {@link SafeFuture} is
   * completed exceptionally. The exception thrown depends on which specific Interruptor was
   * triggered
   *
   * <p>The key feature of this method is that {@code interruptFuture} contained in Interruptor
   * doesn't hold the reference to dependent futures after they complete. It's desired to consider
   * this for long living interrupting futures to avoid memory leaks
   *
   * @param interruptors a set of interruptors which futures trigger interruption if complete
   *     (normally or exceptionally)
   * @see #createInterruptor(CompletableFuture, Supplier)
   */
  // The result of anyOf() future is ignored since it is used just to handle completion
  // of any future. All possible outcomes are propagated to the returned future instance
  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<T> orInterrupt(Interruptor... interruptors) {
    CompletableFuture<?>[] allFuts = new CompletableFuture<?>[interruptors.length + 1];
    allFuts[0] = this;
    for (int i = 0; i < interruptors.length; i++) {
      allFuts[i + 1] = interruptors[i].interruptFuture;
    }
    SafeFuture<T> ret = new SafeFuture<>();
    anyOf(allFuts)
        .whenComplete(
            (res, err) -> {
              if (this.isDone()) {
                this.propagateTo(ret);
              } else {
                for (Interruptor interruptor : interruptors) {
                  if (interruptor.interruptFuture.isDone()) {
                    try {
                      interruptor.getInterruptFuture().get();
                      ret.completeExceptionally(interruptor.getExceptionSupplier().get());
                    } catch (Exception e) {
                      ret.completeExceptionally(e);
                    }
                  }
                }
              }
            });
    return ret;
  }

  /**
   * Class containing an interrupting Future and exception supplier which produces exception if
   * interrupting Future is triggered
   *
   * @see #createInterruptor(CompletableFuture, Supplier)
   * @see #orInterrupt(Interruptor...)
   * @see #notInterrupted(Interruptor...)
   */
  public static class Interruptor {
    private final CompletableFuture<?> interruptFuture;
    private final Supplier<Exception> exceptionSupplier;

    private Interruptor(
        CompletableFuture<?> interruptFuture, Supplier<Exception> exceptionSupplier) {
      this.interruptFuture = interruptFuture;
      this.exceptionSupplier = exceptionSupplier;
    }

    private CompletableFuture<?> getInterruptFuture() {
      return interruptFuture;
    }

    private Supplier<Exception> getExceptionSupplier() {
      return exceptionSupplier;
    }
  }
}
