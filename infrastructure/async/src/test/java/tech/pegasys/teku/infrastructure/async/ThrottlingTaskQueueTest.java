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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ThrottlingTaskQueueTest {

  private static final int MAXIMUM_CONCURRENT_TASKS = 3;

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();

  private final ThrottlingTaskQueue taskQueue =
      ThrottlingTaskQueue.create(
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
                  return taskQueue.queueTask(() -> request);
                })
            .collect(Collectors.toList());

    assertThat(getQueuedTasksGaugeValue()).isEqualTo(97);
    assertThat(taskQueue.getInflightTaskCount()).isEqualTo(3);

    stubAsyncRunner.executeQueuedActions();

    requests.forEach(request -> assertThat(request).isCompleted());
  }

  private double getQueuedTasksGaugeValue() {
    return stubMetricsSystem.getGauge(TekuMetricCategory.BEACON, "test_metric").getValue();
  }
}
