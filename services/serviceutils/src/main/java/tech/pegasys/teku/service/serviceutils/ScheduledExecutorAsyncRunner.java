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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

  private final ScheduledThreadPoolExecutor executorService;

  private ScheduledExecutorAsyncRunner(
      final String name,
      final ScheduledThreadPoolExecutor executorService,
      final MetricsSystem metricsSystem) {
    this.executorService = executorService;
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_queue_size",
        "Current size of the executor task queue",
        () -> executorService.getQueue().size());
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_thread_pool_size",
        "Current number of threads in the executor thread pool",
        executorService::getPoolSize);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_thread_active_count",
        "Current number of threads executing tasks for this executor",
        executorService::getActiveCount);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.EXECUTOR,
        name + "_largest_thread_count",
        "Largest number of threads that have ever been in the pool for this executor",
        executorService::getLargestPoolSize);
  }

  static AsyncRunner create(
      final String name, final int maxThreads, final MetricsSystem metricsSystem) {
    final ScheduledThreadPoolExecutor executorService =
        new ScheduledThreadPoolExecutor(
            1,
            new ThreadFactoryBuilder().setNameFormat(name + "-async-%d").setDaemon(true).build());
    executorService.setRemoveOnCancelPolicy(true);
    executorService.setMaximumPoolSize(maxThreads);
    return new ScheduledExecutorAsyncRunner(name, executorService, metricsSystem);
  }

  @Override
  public <U> SafeFuture<U> runAsync(final Supplier<SafeFuture<U>> action) {
    return runTask(action, executorService::execute);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public <U> SafeFuture<U> runAfterDelay(
      final Supplier<SafeFuture<U>> action, final long delayAmount, final TimeUnit delayUnit) {
    return runTask(action, task -> executorService.schedule(task, delayAmount, delayUnit));
  }

  private <U> SafeFuture<U> runTask(
      final Supplier<SafeFuture<U>> action, final Consumer<Runnable> scheduler) {
    final SafeFuture<U> result = new SafeFuture<>();
    try {
      scheduler.accept(() -> runTask(action, result));
    } catch (final RejectedExecutionException e) {
      LOG.debug("Execution rejected", e);
    } catch (final Throwable t) {
      result.completeExceptionally(t);
    }
    return result;
  }

  private <U> void runTask(final Supplier<SafeFuture<U>> action, final SafeFuture<U> result) {
    try {
      action.get().propagateTo(result);
    } catch (final Throwable t) {
      result.completeExceptionally(t);
    }
  }
}
