/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.infrastructure.async;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public class LimitedThrottlingTaskQueue implements TaskQueue {
  private final TaskQueue delegate;
  private final int maximumQueueSize;

  public LimitedThrottlingTaskQueue(final TaskQueue delegate, final int maximumQueueSize) {
    this.delegate = delegate;
    this.maximumQueueSize = maximumQueueSize;
  }

  @Override
  public synchronized <T> SafeFuture<T> queueTask(final Supplier<SafeFuture<T>> request) {
    if (delegate.getQueuedTasksCount() >= maximumQueueSize) {
      return SafeFuture.failedFuture(new RejectedExecutionException("Task queue is full"));
    }
    return delegate.queueTask(request);
  }

  @Override
  public synchronized int getQueuedTasksCount() {
    return delegate.getQueuedTasksCount();
  }

  @Override
  public synchronized int getInflightTaskCount() {
    return delegate.getInflightTaskCount();
  }
}
