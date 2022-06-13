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

package tech.pegasys.teku.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.RequestCounter.RequestOutcome;

class RequestCounterTest {

  public static final String METRIC_NAME = "metricName";
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final RequestCounter counter =
      RequestCounter.create(metricsSystem, TekuMetricCategory.BEACON, METRIC_NAME, "Some help");

  @Test
  public void shouldIncrementSuccessCounter() {
    counter.onSuccess();
    assertThat(getValue(RequestOutcome.SUCCESS)).isEqualTo(1);
    assertThat(getValue(RequestOutcome.ERROR)).isZero();
    assertThat(getValue(RequestOutcome.DATA_UNAVAILABLE)).isZero();
  }

  @Test
  public void shouldIncrementErrorCounter() {
    counter.onError();
    assertThat(getValue(RequestOutcome.ERROR)).isEqualTo(1);
    assertThat(getValue(RequestOutcome.SUCCESS)).isZero();
    assertThat(getValue(RequestOutcome.DATA_UNAVAILABLE)).isZero();
  }

  @Test
  public void shouldIncrementDataUnavailableCounter() {
    counter.onDataUnavailable();
    assertThat(getValue(RequestOutcome.DATA_UNAVAILABLE)).isEqualTo(1);
    assertThat(getValue(RequestOutcome.ERROR)).isZero();
    assertThat(getValue(RequestOutcome.SUCCESS)).isZero();
  }

  private long getValue(final RequestOutcome requestOutcome) {
    return metricsSystem
        .getCounter(TekuMetricCategory.BEACON, METRIC_NAME)
        .getValue(requestOutcome.name());
  }
}
