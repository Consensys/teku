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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ThrottlingTaskQueue {
  private static final Logger LOG = LogManager.getLogger();
  private final int maximumConcurrentRequests;
  private volatile Queue<Runnable> queuedRequests = new ConcurrentLinkedQueue<>();
  private volatile AtomicInteger activeTasks = new AtomicInteger(0);

  private final SettableGauge queueSizeGauge;

  public ThrottlingTaskQueue(
      final int maximumConcurrentRequests,
      final MetricsSystem metricsSystem,
      final TekuMetricCategory metricCategory,
      final String metricsPrefix) {
    this.maximumConcurrentRequests = maximumConcurrentRequests;

    this.queueSizeGauge =
        SettableGauge.create(
            metricsSystem,
            metricCategory,
            metricsPrefix + "_tasks_queued",
            "Number of tasks queued");
  }

  public <T> SafeFuture<T> queueRequest(final Supplier<SafeFuture<T>> request) {
    final SafeFuture<T> future = new SafeFuture<>();
    queuedRequests.add(
        () -> {
          final SafeFuture<T> requestFuture = request.get();
          requestFuture.propagateTo(future);
          requestFuture.always(this::requestComplete);
        });
    queueSizeGauge.set(queuedRequests.size());
    processQueuedRequests();
    return future;
  }

  private void requestComplete() {
    activeTasks.decrementAndGet();
    processQueuedRequests();
  }

  private void processQueuedRequests() {
    while (activeTasks.get() < maximumConcurrentRequests && !queuedRequests.isEmpty()) {
      // technically this could go beyond maximumConcurrentRequests
      final int i = activeTasks.incrementAndGet();
      if (i > maximumConcurrentRequests) {
        LOG.trace("Skipping queued requests, already exceeding maximum as activeTasks is {}", i);
        activeTasks.decrementAndGet();
        break;
      }
      queuedRequests.remove().run();
      queueSizeGauge.set(queuedRequests.size());
    }
  }
}
