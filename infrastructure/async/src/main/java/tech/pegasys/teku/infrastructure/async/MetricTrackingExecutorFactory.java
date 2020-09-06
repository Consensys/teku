/*
 * Copyright 2020 ConsenSys AG.
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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class MetricTrackingExecutorFactory {

  private final MetricsSystem metricsSystem;

  public MetricTrackingExecutorFactory(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
  }

  /**
   * Creates a new {@link ExecutorService} which creates up to {@code maxThreads} threads as needed
   * and when all threads are busy, queues up to {@code maxQueueSize} further tasks to execute when
   * threads are available. When the maximum thread count and queue size are reached tasks are
   * rejected with {@link java.util.concurrent.RejectedExecutionException}
   *
   * <p>Metrics about the number of threads and queue length are automatically captured.
   *
   * @param name the name to use as a prefix in metric names. Must be unique.
   * @param maxThreads the maximum number of threads to run at any one time.
   * @param maxQueueSize the maximum capacity of the pending task queue.
   * @param threadFactory the thread factory to use when creating threads.
   * @return the new {@link ExecutorService}
   */
  public ExecutorService newCachedThreadPool(
      final String name,
      final int maxThreads,
      final int maxQueueSize,
      final ThreadFactory threadFactory) {

    // ThreadPoolExecutor has a weird API. maximumThreadCount only applies if you use a
    // SynchronousQueue but then tasks are rejected once max threads are reached instead of being
    // queued. So we use a blocking queue to ensure there is some limit on the queue size but that
    // means that the maximum number of threads is ignored and only the core thread pool size is
    // used. So, we set maximum and core thread pool to the same value and allow core threads to
    // time out and exit if they are unused.

    final ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            maxThreads,
            maxThreads,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(maxQueueSize),
            threadFactory);
    executor.allowCoreThreadTimeOut(true);

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_queue_size",
        "Current size of the executor task queue",
        () -> executor.getQueue().size());
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_thread_pool_size",
        "Current number of threads in the executor thread pool",
        executor::getPoolSize);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_thread_active_count",
        "Current number of threads executing tasks for this executor",
        executor::getActiveCount);

    return executor;
  }
}
