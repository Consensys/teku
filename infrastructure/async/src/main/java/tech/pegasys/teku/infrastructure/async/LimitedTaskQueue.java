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

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

/**
 * A TaskQueue that limits the number of queued tasks. If the limit is reached, new tasks will be
 * rejected. This is useful to prevent unbounded memory usage when tasks are being added faster than
 * they can be processed. Passing a {@link ThrottlingTaskQueue} as the delegate will also limit the
 * number of concurrently executing tasks.
 */
public class LimitedTaskQueue implements TaskQueue {
  private final TaskQueue delegate;
  private final int maximumQueueSize;
  private volatile long rejectedTaskCount = 0;

  public static class QueueIsFullException extends RejectedExecutionException {
    public QueueIsFullException() {
      super("Task queue is full");
    }
  }

  public static boolean isQueueIsFullException(final Throwable error) {
    return error instanceof QueueIsFullException
        || (error.getCause() != null && isQueueIsFullException(error.getCause()));
  }

  public static LimitedTaskQueue create(
      final TaskQueue delegate,
      final int maximumQueueSize,
      final MetricsSystem metricsSystem,
      final TekuMetricCategory metricCategory,
      final String metricName) {
    final LimitedTaskQueue limitedQueue = new LimitedTaskQueue(delegate, maximumQueueSize);
    metricsSystem.createLongGauge(
        metricCategory,
        metricName,
        "Number of tasks rejected by the queue",
        () -> limitedQueue.rejectedTaskCount);
    return limitedQueue;
  }

  private LimitedTaskQueue(final TaskQueue delegate, final int maximumQueueSize) {
    this.delegate = delegate;
    this.maximumQueueSize = maximumQueueSize;
  }

  @Override
  public <T> SafeFuture<T> queueTask(final Supplier<SafeFuture<T>> request) {
    synchronized (delegate) {
      if (delegate.getQueuedTasksCount() >= maximumQueueSize) {
        rejectedTaskCount++;
        return SafeFuture.failedFuture(new QueueIsFullException());
      }
      return delegate.queueTask(request);
    }
  }

  @Override
  public int getQueuedTasksCount() {
    return delegate.getQueuedTasksCount();
  }

  @VisibleForTesting
  @Override
  public int getInflightTaskCount() {
    return delegate.getInflightTaskCount();
  }
}
