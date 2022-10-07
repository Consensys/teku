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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ThrottlingTaskQueueWithPriorityTest {

  private static final int MAXIMUM_CONCURRENT_TASKS = 3;

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();

  private final ThrottlingTaskQueueWithPriority taskQueue =
      ThrottlingTaskQueueWithPriority.create(
          MAXIMUM_CONCURRENT_TASKS, stubMetricsSystem, TekuMetricCategory.BEACON, "test_metric");

  @Test
  public void throttlesRequests() {
    final List<SafeFuture<Void>> requests =
        IntStream.range(0, 100)
            .mapToObj(
                element -> {
                  final SafeFuture<Void> request =
                      stubAsyncRunner.runAsync(
                          () -> {
                            assertThat(taskQueue.getInflightTaskCount())
                                .isLessThanOrEqualTo(MAXIMUM_CONCURRENT_TASKS);
                          });
                  // prioritize 1/3 of requests
                  if (element % 3 == 0) {
                    return taskQueue.queueTask(() -> request, true);
                  }
                  return taskQueue.queueTask(() -> request);
                })
            .collect(Collectors.toList());

    assertThat(getNumberOfQueuedTasksMetric()).isEqualTo(97);
    assertThat(taskQueue.getInflightTaskCount()).isEqualTo(3);

    stubAsyncRunner.executeQueuedActions();

    requests.forEach(request -> assertThat(request).isCompleted());
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void prioritizesRequests() {
    final SafeFuture<Void> initialRequest = new SafeFuture<>();
    final SafeFuture<Void> prioritizedRequest = new SafeFuture<>();
    final SafeFuture<Void> normalRequest = new SafeFuture<>();

    final AtomicBoolean priorityFirst = new AtomicBoolean(false);

    // fill queue
    IntStream.range(0, MAXIMUM_CONCURRENT_TASKS)
        .forEach(__ -> taskQueue.queueTask(() -> initialRequest));
    final SafeFuture<Void> assertion =
        taskQueue.queueTask(
            () -> {
              // make sure the prioritized request is ran first
              // even though It has been queued after this one
              assertThat(priorityFirst).isTrue();
              return normalRequest;
            });
    taskQueue.queueTask(
        () -> {
          priorityFirst.set(true);
          return prioritizedRequest;
        },
        true);

    assertThat(getNumberOfQueuedTasksMetric()).isEqualTo(2);
    assertThat(taskQueue.getInflightTaskCount()).isEqualTo(3);

    initialRequest.complete(null);
    normalRequest.complete(null);
    prioritizedRequest.complete(null);

    assertThat(assertion).isCompleted();
  }

  private double getNumberOfQueuedTasksMetric() {
    return stubMetricsSystem.getGauge(TekuMetricCategory.BEACON, "test_metric").getValue();
  }
}
