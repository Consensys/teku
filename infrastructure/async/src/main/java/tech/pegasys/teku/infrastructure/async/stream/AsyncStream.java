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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/** Similar to {@link java.util.stream.Stream} but may perform async operations */
public interface AsyncStream<T> extends AsyncStreamTransform<T>, AsyncStreamConsume<T> {

  static <T> AsyncStream<T> empty() {
    return of();
  }

  static <T> AsyncStream<T> exceptional(Throwable error) {
    AsyncStreamPublisher<T> ret = createPublisher(1);
    ret.onError(error);
    return ret;
  }

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

  static <T> AsyncStreamPublisher<T> createPublisher(int maxBufferSize) {
    return new BufferingStreamPublisher<>(maxBufferSize);
  }
}
