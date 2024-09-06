/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.infrastructure.async.stream;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

abstract class AsyncIterator<T> implements AsyncStream<T> {

  abstract void iterate(AsyncIteratorCallback<T> callback);

  @Override
  public AsyncIterator<T> filter(Predicate<T> filter) {
    return OperationAsyncIterator.create(
        this, sourceCallback -> new FilteringIteratorCallback<>(sourceCallback, filter));
  }

  @Override
  public AsyncIterator<T> limit(long limit) {
    return OperationAsyncIterator.create(
        this, sourceCallback -> new LimitIteratorCallback<>(sourceCallback, limit));
  }

  @Override
  public AsyncStream<T> peek(Consumer<T> visitor) {
    return OperationAsyncIterator.create(
        this,
        sourceCallback ->
            new AbstractDelegatingIteratorCallback<>(sourceCallback) {
              @Override
              public SafeFuture<Boolean> onNext(T t) {
                visitor.accept(t);
                return delegate.onNext(t);
              }
            });
  }

  @Override
  public <R> AsyncIterator<R> map(Function<T, R> mapper) {
    return OperationAsyncIterator.create(
        this, sourceCallback -> new MapIteratorCallback<>(sourceCallback, mapper));
  }

  @Override
  public <R> AsyncIterator<R> flatMap(Function<T, AsyncStream<R>> toStreamMapper) {
    Function<T, AsyncIterator<R>> toIteratorMapper =
        toStreamMapper.andThen(stream -> (AsyncIterator<R>) stream);
    return OperationAsyncIterator.create(
        this,
        sourceCallback ->
            new MapIteratorCallback<>(
                new FlattenIteratorCallback<>(sourceCallback), toIteratorMapper));
  }

  @Override
  public SafeFuture<Void> forEach(Consumer<T> consumer) {
    SafeFuture<Void> ret = new SafeFuture<>();
    iterate(
        new AsyncIteratorCallback<T>() {
          @Override
          public SafeFuture<Boolean> onNext(T t) {
            consumer.accept(t);
            return TRUE_FUTURE;
          }

          @Override
          public void onComplete() {
            ret.complete(null);
          }

          @Override
          public void onError(Throwable t) {
            ret.completeExceptionally(t);
          }
        });
    return ret;
  }

  @Override
  public SafeFuture<Optional<T>> findFirst() {
    SafeFuture<Optional<T>> ret = new SafeFuture<>();
    iterate(
        new AsyncIteratorCallback<T>() {
          @Override
          public SafeFuture<Boolean> onNext(T t) {
            ret.complete(Optional.ofNullable(t));
            return FALSE_FUTURE;
          }

          @Override
          public void onComplete() {
            ret.complete(Optional.empty());
          }

          @Override
          public void onError(Throwable t) {
            ret.completeExceptionally(t);
          }
        });
    return ret;
  }

  @Override
  public <C extends Collection<T>> SafeFuture<C> collect(C targetCollection) {
    return new AsyncIteratorCollector<>(this).collect(targetCollection);
  }
}
