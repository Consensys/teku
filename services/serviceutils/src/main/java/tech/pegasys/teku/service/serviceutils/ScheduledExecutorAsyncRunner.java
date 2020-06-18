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

package tech.pegasys.teku.service.serviceutils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;

class ScheduledExecutorAsyncRunner implements AsyncRunner {
  private static final Logger LOG = LogManager.getLogger();
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final ScheduledExecutorService scheduler;
  private final ExecutorService workerPool;

  ScheduledExecutorAsyncRunner(
      final ScheduledExecutorService scheduler, final ExecutorService workerPool) {
    this.scheduler = scheduler;
    this.workerPool = workerPool;
  }

  static AsyncRunner create(
      final String name, final int maxThreads, final MetricsSystem metricsSystem) {
    final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat(name + "-async-scheduler-%d")
                .setDaemon(true)
                .build());
    final ThreadPoolExecutor workerPool =
        new ThreadPoolExecutor(
            1,
            maxThreads,
            60,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setNameFormat(name + "-async-%d").setDaemon(true).build());

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_queue_size",
        "Current size of the executor task queue",
        () -> workerPool.getQueue().size());
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_thread_pool_size",
        "Current number of threads in the executor thread pool",
        workerPool::getPoolSize);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_thread_active_count",
        "Current number of threads executing tasks for this executor",
        workerPool::getActiveCount);

    return new ScheduledExecutorAsyncRunner(scheduler, workerPool);
  }

  @Override
  public <U> SafeFuture<U> runAsync(final Supplier<SafeFuture<U>> action) {
    return runTask(action, workerPool::execute);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public <U> SafeFuture<U> runAfterDelay(
      final Supplier<SafeFuture<U>> action, final long delayAmount, final TimeUnit delayUnit) {
    return runTask(
        action, task -> scheduler.schedule(() -> workerPool.execute(task), delayAmount, delayUnit));
  }

  @Override
  public void shutdown() {
    // All threads are daemon threads so don't wait for them to actually stop
    shutdown.set(true);
    scheduler.shutdownNow();
    workerPool.shutdownNow();
  }

  private <U> SafeFuture<U> runTask(
      final Supplier<SafeFuture<U>> action, final Consumer<Runnable> executor) {
    if (shutdown.get()) {
      LOG.debug("Ignoring async task because shutdown is in progress");
      return new SafeFuture<>();
    }
    final SafeFuture<U> result = new SafeFuture<>();
    try {
      executor.accept(() -> SafeFuture.ofComposed(action::get).propagateTo(result));
    } catch (final Throwable t) {
      result.completeExceptionally(t);
    }
    return result;
  }
}
