/*
 * Copyright ConsenSys Software Inc., 2022
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
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ThrottlingTaskQueue {

  private final Deque<Runnable> queuedTasks = new ConcurrentLinkedDeque<>();

  private final int maximumConcurrentTasks;

  private int inflightTaskCount = 0;

  public ThrottlingTaskQueue(
      final int maximumConcurrentTasks,
      final MetricsSystem metricsSystem,
      final TekuMetricCategory metricCategory,
      final String metricName) {
    this.maximumConcurrentTasks = maximumConcurrentTasks;

    metricsSystem.createGauge(
        metricCategory, metricName, "Number of tasks queued", queuedTasks::size);
  }

  public <T> SafeFuture<T> queueTask(
      final Supplier<SafeFuture<T>> request, final boolean prioritize) {
    final SafeFuture<T> future = new SafeFuture<>();
    final Runnable taskToQueue =
        () -> {
          final SafeFuture<T> requestFuture = request.get();
          requestFuture.propagateTo(future);
          requestFuture.always(this::taskComplete);
        };
    if (prioritize) {
      queuedTasks.addFirst(taskToQueue);
    } else {
      queuedTasks.addLast(taskToQueue);
    }
    processQueuedTasks();
    return future;
  }

  public <T> SafeFuture<T> queueTask(final Supplier<SafeFuture<T>> request) {
    return queueTask(request, false);
  }

  @VisibleForTesting
  int getInflightTaskCount() {
    return inflightTaskCount;
  }

  private synchronized void taskComplete() {
    inflightTaskCount--;
    processQueuedTasks();
  }

  private synchronized void processQueuedTasks() {
    while (inflightTaskCount < maximumConcurrentTasks && !queuedTasks.isEmpty()) {
      inflightTaskCount++;
      queuedTasks.removeFirst().run();
    }
  }
}
