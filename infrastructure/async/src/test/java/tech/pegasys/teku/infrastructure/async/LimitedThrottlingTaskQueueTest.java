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

import java.util.List;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.LimitedThrottlingTaskQueue.QueueIsFullException;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class LimitedThrottlingTaskQueueTest extends ThrottlingTaskQueueTest {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  protected TaskQueue createThrottlingTaskQueue() {
    return LimitedThrottlingTaskQueue.create(
        super.createThrottlingTaskQueue(),
        15,
        stubMetricsSystem,
        TekuMetricCategory.BEACON,
        "test_rejected_metric");
  }

  @Test
  public void rejectsWhenFull() {
    taskQueue = createThrottlingTaskQueue();

    final int totalTasks = 20;
    final int maxQueueSize = 15;
    final int expectedRejected = totalTasks - maxQueueSize - MAXIMUM_CONCURRENT_TASKS;
    final int[] rejectedCount = {0};
    final List<SafeFuture<Void>> requests =
        IntStream.range(0, totalTasks)
            .mapToObj(
                element ->
                    taskQueue
                        .queueTask(
                            () ->
                                stubAsyncRunner.runAsync(
                                    () -> {
                                      LOG.info("Running task {}", element);
                                      assertThat(taskQueue.getInflightTaskCount())
                                          .isLessThanOrEqualTo(MAXIMUM_CONCURRENT_TASKS);
                                    }))
                        .exceptionally(
                            err -> {
                              LOG.info("Task {} was rejected", element);
                              assertThat(err).isInstanceOf(QueueIsFullException.class);
                              rejectedCount[0]++;
                              return null;
                            }))
            .toList();

    // stubRunner will run whatever is active
    stubAsyncRunner.executeQueuedActions();
    assertThat(rejectedCount[0]).isEqualTo(expectedRejected);
    assertThat(
            stubMetricsSystem
                .getGauge(TekuMetricCategory.BEACON, "test_rejected_metric")
                .getValue())
        .isEqualTo(expectedRejected);
    checkQueueProgress(
        requests,
        maxQueueSize - MAXIMUM_CONCURRENT_TASKS,
        MAXIMUM_CONCURRENT_TASKS,
        MAXIMUM_CONCURRENT_TASKS + expectedRejected);
  }
}
