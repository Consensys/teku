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

abstract class AbstractDelegatingStreamHandler<S, T> implements AsyncStreamHandler<T> {

  protected final AsyncStreamHandler<S> delegate;

  protected AbstractDelegatingStreamHandler(final AsyncStreamHandler<S> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onComplete() {
    delegate.onComplete();
  }

  @Override
  public void onError(final Throwable t) {
    delegate.onError(t);
  }
}
