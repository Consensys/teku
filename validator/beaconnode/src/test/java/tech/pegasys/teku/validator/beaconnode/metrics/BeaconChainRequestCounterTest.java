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

package tech.pegasys.teku.validator.beaconnode.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.validator.beaconnode.metrics.BeaconChainRequestCounter.RequestOutcome;

class BeaconChainRequestCounterTest {

  public static final String METRIC_NAME = "metricName";
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final BeaconChainRequestCounter counter =
      BeaconChainRequestCounter.create(metricsSystem, METRIC_NAME, "Some help");

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
        .getCounter(TekuMetricCategory.VALIDATOR, METRIC_NAME)
        .getValue(requestOutcome.name());
  }
}
