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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;

public class MultiThreadedThrottlingQueueTest {
  private static final int MAXIMUM_CONCURRENT_TASKS = 3;
  private static final Logger LOG = LogManager.getLogger();
  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();
  private final MetricTrackingExecutorFactory metricTrackingExecutorFactory =
      new MetricTrackingExecutorFactory(stubMetricsSystem);
  private final DefaultAsyncRunnerFactory asyncRunnerFactory =
      new DefaultAsyncRunnerFactory(metricTrackingExecutorFactory);
  private final AsyncRunner asyncRunner = asyncRunnerFactory.create("test", 10, 50, 5);

  // Primarily a practical demo of tasks interleaving and being limited
  @Test
  public void asyncTaskLimitDemo() throws InterruptedException {
    final ThrottlingTaskQueue queue = new ThrottlingTaskQueue(MAXIMUM_CONCURRENT_TASKS);
    final List<SafeFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      final int taskNumber = i;
      futures.add(
          queue.queueTask(
              () ->
                  asyncRunner.runAsync(
                      () -> {
                        LOG.info("task {} starting", taskNumber);
                        int progressCounter = 0;
                        // 5 x 20 = 100ms at minimum to run
                        while (progressCounter < 5) {
                          progressCounter++;
                          LOG.info("task {}:{}", taskNumber, progressCounter);
                          Thread.sleep(20);
                        }
                        LOG.info("task {} done", taskNumber);
                      })));
    }
    // should never exceed max tasks in flight
    assertThat(queue.getInflightTaskCount()).isLessThanOrEqualTo(MAXIMUM_CONCURRENT_TASKS);

    /*
     * Running 20 tasks, and each takes at least 100ms.
     * This test runs 3 at a time by default, so in 100ms or so we'd get 3 tasks done.
     * In 300ms, it's not possible to run 20 tasks if performing correctly.
     */
    Thread.sleep(300);
    assertThat(futures.stream().filter(CompletableFuture::isDone).count())
        .isGreaterThanOrEqualTo(3)
        .isLessThan(10);

    // cleanup
    futures.forEach(future -> future.cancel(true));
  }
}
