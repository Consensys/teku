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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface AsyncStreamConsume<T> extends BaseAsyncStreamConsume<T>, AsyncStreamTransform<T> {

  default <A, R> SafeFuture<R> collect(Collector<T, A, R> collector) {
    AsyncIteratorCollector<T, A, R> asyncIteratorCollector =
        new AsyncIteratorCollector<>(collector);
    consume(asyncIteratorCollector);
    return asyncIteratorCollector.getPromise();
  }

  default SafeFuture<Optional<T>> findFirst() {
    return this.limit(1)
        .toList()
        .thenApply(l -> l.isEmpty() ? Optional.empty() : Optional.of(l.getFirst()));
  }

  default SafeFuture<Void> forEach(Consumer<T> consumer) {
    return collect(Collector.of(() -> null, (a, t) -> consumer.accept(t), noCallBinaryOperator()));
  }

  default <C extends Collection<T>> SafeFuture<C> collect(C targetCollection) {
    return collect(Collectors.toCollection(() -> targetCollection));
  }

  default SafeFuture<List<T>> toList() {
    return collect(Collectors.toUnmodifiableList());
  }

  default SafeFuture<Optional<T>> findLast() {
    return collectLast(1)
        .thenApply(l -> l.isEmpty() ? Optional.empty() : Optional.of(l.getFirst()));
  }

  default SafeFuture<List<T>> collectLast(int count) {
    class CircularBuf<C> {
      final ArrayDeque<C> buf;
      final int maxSize;

      public CircularBuf(int maxSize) {
        buf = new ArrayDeque<>(maxSize);
        this.maxSize = maxSize;
      }

      public void add(C t) {
        if (buf.size() == maxSize) {
          buf.removeFirst();
        }
        buf.add(t);
      }
    }
    return collect(
        Collector.of(
            () -> new CircularBuf<T>(count),
            CircularBuf::add,
            noCallBinaryOperator(),
            buf -> buf.buf.stream().toList()));
  }

  private static <C> BinaryOperator<C> noCallBinaryOperator() {
    return (c, c2) -> {
      throw new UnsupportedOperationException("Shouldn't be called");
    };
  }
}
