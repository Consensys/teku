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

import tech.pegasys.teku.infrastructure.async.SafeFuture;

class SliceIteratorCallback<T> extends AbstractDelegatingIteratorCallback<T, T> {

  private final BaseAsyncStreamTransform.BaseSlicer<T> slicer;

  protected SliceIteratorCallback(
      AsyncIteratorCallback<T> delegate, BaseAsyncStreamTransform.BaseSlicer<T> slicer) {
    super(delegate);
    this.slicer = slicer;
  }

  @Override
  public SafeFuture<Boolean> onNext(T t) {
    BaseAsyncStreamTransform.SliceResult sliceResult = slicer.slice(t);
    return switch (sliceResult) {
      case CONTINUE -> delegate.onNext(t);
      case SKIP_AND_STOP -> FALSE_FUTURE;
      case INCLUDE_AND_STOP -> delegate.onNext(t).thenApply(__ -> false);
    };
  }
}
