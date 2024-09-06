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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/** Similar to {@link java.util.stream.Stream} but may perform async operations */
public interface AsyncStream<T> {

  // builders

  @SafeVarargs
  static <T> AsyncStream<T> of(T... elements) {
    return create(List.of(elements).iterator());
  }

  static <T> AsyncStream<T> create(Stream<T> stream) {
    return create(stream.iterator());
  }

  static <T> AsyncStream<T> create(Iterator<T> iterator) {
    return new SyncToAsyncIteratorImpl<>(iterator);
  }

  static <T> AsyncStream<T> create(CompletionStage<T> future) {
    return new FutureAsyncIteratorImpl<>(future);
  }

  // transformations

  AsyncStream<T> filter(Predicate<T> filter);

  AsyncStream<T> limit(long limit);

  <R> AsyncStream<R> map(Function<T, R> mapper);

  AsyncStream<T> peek(Consumer<T> visitor);

  default <R> AsyncStream<R> mapAsync(Function<T, SafeFuture<R>> mapper) {
    return flatMap(e -> create(mapper.apply(e)));
  }

  <R> AsyncStream<R> flatMap(Function<T, AsyncStream<R>> toStreamMapper);

  // terminal operators

  SafeFuture<Optional<T>> findFirst();

  SafeFuture<Void> forEach(Consumer<T> consumer);

  <C extends Collection<T>> SafeFuture<C> collect(C targetCollection);

  default SafeFuture<List<T>> toList() {
    return collect(new ArrayList<>());
  }
}
