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

import static tech.pegasys.teku.infrastructure.async.stream.BaseAsyncStreamTransform.SliceResult.CONTINUE;
import static tech.pegasys.teku.infrastructure.async.stream.BaseAsyncStreamTransform.SliceResult.INCLUDE_AND_STOP;
import static tech.pegasys.teku.infrastructure.async.stream.BaseAsyncStreamTransform.SliceResult.SKIP_AND_STOP;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public interface AsyncStreamTransform<T> extends BaseAsyncStreamTransform<T> {

  interface Slicer<T> extends BaseSlicer<T> {

    static <T> Slicer<T> limit(long count) {
      return new Slicer<>() {
        private final AtomicLong remainCount = new AtomicLong(count);

        @Override
        public SliceResult slice(T element) {
          return remainCount.decrementAndGet() > 0 ? CONTINUE : INCLUDE_AND_STOP;
        }
      };
    }

    static <T> Slicer<T> takeWhile(Predicate<T> condition) {
      return t -> condition.test(t) ? CONTINUE : SKIP_AND_STOP;
    }

    default Slicer<T> then(Slicer<T> nextSlicer) {
      return new Slicer<T>() {
        private boolean thisSlicerCompleted = false;

        @Override
        public SliceResult slice(T element) {
          if (thisSlicerCompleted) {
            return nextSlicer.slice(element);
          } else {
            SliceResult result = Slicer.this.slice(element);
            return switch (result) {
              case CONTINUE -> result;
              case SKIP_AND_STOP -> {
                thisSlicerCompleted = true;
                yield nextSlicer.slice(element);
              }
              case INCLUDE_AND_STOP -> {
                thisSlicerCompleted = true;
                yield CONTINUE;
              }
            };
          }
        }
      };
    }
  }

  // transformation

  // Suboptimal reference implementation
  // To be overridden in implementation with a faster variant
  default <R> AsyncStream<R> map(Function<T, R> mapper) {
    return flatMap(t -> AsyncStream.of(mapper.apply(t)));
  }

  // Suboptimal reference implementation
  // To be overridden in implementation with a faster variant
  default AsyncStream<T> filter(Predicate<T> filter) {
    return flatMap(t -> filter.test(t) ? AsyncStream.of(t) : AsyncStream.empty());
  }

  default AsyncStream<T> peek(Consumer<T> visitor) {
    return map(
        t -> {
          visitor.accept(t);
          return t;
        });
  }

  default <R> AsyncStream<R> mapAsync(Function<T, SafeFuture<R>> mapper) {
    return flatMap(e -> AsyncStream.create(mapper.apply(e)));
  }

  // slicing

  default AsyncStream<T> limit(long count) {
    return slice(Slicer.limit(count));
  }

  default AsyncStream<T> takeWhile(Predicate<T> whileCondition) {
    return slice(Slicer.takeWhile(whileCondition));
  }

  default AsyncStream<T> takeUntil(Predicate<T> untilCondition, boolean includeLast) {
    Slicer<T> whileSlicer = Slicer.takeWhile(untilCondition.negate());
    Slicer<T> untilSlicer = includeLast ? whileSlicer.then(Slicer.limit(1)) : whileSlicer;
    return slice(untilSlicer);
  }
}
