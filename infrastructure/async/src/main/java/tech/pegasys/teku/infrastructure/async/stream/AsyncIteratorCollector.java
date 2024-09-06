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
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class AsyncIteratorCollector<T> {

  private final AsyncIterator<T> iterator;

  public AsyncIteratorCollector(AsyncIterator<T> iterator) {
    this.iterator = iterator;
  }

  public <C extends Collection<T>> SafeFuture<C> collect(C collection) {
    SafeFuture<C> promise = new SafeFuture<>();
    iterator.iterate(
        new AsyncIteratorCallback<T>() {
          @Override
          public SafeFuture<Boolean> onNext(T t) {
            synchronized (collection) {
              collection.add(t);
            }
            return TRUE_FUTURE;
          }

          @Override
          public void onComplete() {
            promise.complete(collection);
          }

          @Override
          public void onError(Throwable t) {
            promise.completeExceptionally(t);
          }
        });
    return promise;
  }
}
