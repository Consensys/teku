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

public class GoodFuture<T> extends CompletableFuture<T> {
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

  public static <U> GoodFuture<U> completedFuture(U value) {
    GoodFuture<U> f = new GoodFuture<>();
    f.complete(value);
    return f;
  }

  public static <U> GoodFuture<U> failedFuture(Throwable ex) {
    GoodFuture<U> f = new GoodFuture<>();
    f.completeExceptionally(ex);
    return f;
  }

  public static <U> GoodFuture<U> of(final CompletionStage<U> stage) {
    if (stage instanceof GoodFuture) {
      return (GoodFuture<U>) stage;
    }
    final GoodFuture<U> goodFuture = new GoodFuture<>();
    stage.whenComplete(
        (result, error) -> {
          if (error != null) {
            goodFuture.completeExceptionally(error);
          } else {
            goodFuture.complete(result);
          }
        });
    return goodFuture;
  }

  public static <U> GoodFuture<Void> allOf(final GoodFuture<?>... futures) {
    return of(CompletableFuture.allOf(futures));
  }

  public static GoodFuture<Object> anyOf(final GoodFuture<?>... futures) {
    return of(CompletableFuture.anyOf(futures));
  }

  @Override
  public <U> GoodFuture<U> newIncompleteFuture() {
    return new GoodFuture<>();
  }

  public void reportExceptions() {
    reportExceptions(this);
  }

  public void finish(final Consumer<T> onSuccess) {
    reportExceptions(thenAccept(onSuccess));
  }

  public void finish(final Runnable onSuccess, final Consumer<Throwable> onError) {
    finish(result -> onSuccess.run(), onError);
  }

  public void finish(final Consumer<T> onSuccess, final Consumer<Throwable> onError) {
    reportExceptions(
        whenComplete(
            (result, error) -> {
              if (error != null) {
                onError.accept(error);
              } else {
                onSuccess.accept(result);
              }
            }));
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> GoodFuture<U> thenApply(final Function<? super T, ? extends U> fn) {
    return (GoodFuture<U>) super.thenApply(fn);
  }

  @Override
  public GoodFuture<Void> thenRun(final Runnable action) {
    return (GoodFuture<Void>) super.thenRun(action);
  }

  @Override
  public GoodFuture<Void> thenAccept(final Consumer<? super T> action) {
    return (GoodFuture<Void>) super.thenAccept(action);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, V> GoodFuture<V> thenCombine(
      final CompletionStage<? extends U> other,
      final BiFunction<? super T, ? super U, ? extends V> fn) {
    return (GoodFuture<V>) super.thenCombine(other, fn);
  }

  @Override
  public <U> GoodFuture<U> thenCompose(final Function<? super T, ? extends CompletionStage<U>> fn) {
    return (GoodFuture<U>) super.thenCompose(fn);
  }

  @Override
  public GoodFuture<T> exceptionally(final Function<Throwable, ? extends T> fn) {
    return (GoodFuture<T>) super.exceptionally(fn);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> GoodFuture<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
    return (GoodFuture<U>) super.handle(fn);
  }

  @Override
  public GoodFuture<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
    return (GoodFuture<T>) super.whenComplete(action);
  }

  @Override
  public GoodFuture<T> orTimeout(final long timeout, final TimeUnit unit) {
    return (GoodFuture<T>) super.orTimeout(timeout, unit);
  }
}
