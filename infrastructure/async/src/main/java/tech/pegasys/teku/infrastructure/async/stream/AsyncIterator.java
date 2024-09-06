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

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

abstract class AsyncIterator<T> implements AsyncStream<T> {

  abstract void iterate(AsyncIteratorCallback<T> callback);

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
  public <R> AsyncIterator<R> map(Function<T, R> mapper) {
    return OperationAsyncIterator.create(
        this, sourceCallback -> new MapIteratorCallback<>(sourceCallback, mapper));
  }

  @Override
  public AsyncIterator<T> filter(Predicate<T> filter) {
    return OperationAsyncIterator.create(
        this, sourceCallback -> new FilteringIteratorCallback<>(sourceCallback, filter));
  }

  @Override
  public <A, R> SafeFuture<R> collect(Collector<T, A, R> collector) {
    return new AsyncIteratorCollector<>(this).collect(collector);
  }

  @Override
  public AsyncStream<T> slice(BaseSlicer<T> slicer) {
    return OperationAsyncIterator.create(
        this, sourceCallback -> new SliceIteratorCallback<>(sourceCallback, slicer));
  }
}
