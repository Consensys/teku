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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ThrottlingTaskQueueWithPriority extends ThrottlingTaskQueue {

  private final Queue<Runnable> queuedPrioritizedTasks = new ConcurrentLinkedQueue<>();

  public static ThrottlingTaskQueueWithPriority create(
      final int maximumConcurrentTasks,
      final MetricsSystem metricsSystem,
      final TekuMetricCategory metricCategory,
      final String metricName) {
    final ThrottlingTaskQueueWithPriority taskQueue =
        new ThrottlingTaskQueueWithPriority(maximumConcurrentTasks);
    final LabelledGauge taskQueueGauge =
        metricsSystem.createLabelledGauge(
            metricCategory, metricName, "Number of tasks queued", "priority");
    taskQueueGauge.labels(taskQueue.queuedTasks::size, "normal");
    taskQueueGauge.labels(taskQueue.queuedPrioritizedTasks::size, "high");
    return taskQueue;
  }

  private ThrottlingTaskQueueWithPriority(final int maximumConcurrentTasks) {
    super(maximumConcurrentTasks);
  }

  public <T> SafeFuture<T> queueTask(
      final Supplier<SafeFuture<T>> request, final boolean prioritize) {
    if (!prioritize) {
      return queueTask(request);
    }
    final SafeFuture<T> target = new SafeFuture<>();
    final Runnable taskToQueue = getTaskToQueue(request, target);
    queuedPrioritizedTasks.add(taskToQueue);
    processQueuedTasks();
    return target;
  }

  @Override
  protected Runnable getTaskToRun() {
    return !queuedPrioritizedTasks.isEmpty()
        ? queuedPrioritizedTasks.remove()
        : queuedTasks.remove();
  }

  @Override
  protected int getQueuedTasksCount() {
    return queuedTasks.size() + queuedPrioritizedTasks.size();
  }
}
