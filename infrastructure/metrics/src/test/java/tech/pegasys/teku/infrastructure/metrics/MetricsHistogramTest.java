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

package tech.pegasys.teku.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.Test;

class MetricsHistogramTest {

  private static final TekuMetricCategory CATEGORY = TekuMetricCategory.BEACON;
  private final ObservableMetricsSystem metricsSystem =
      new PrometheusMetricsSystem(Set.of(CATEGORY), true);

  @Test
  void shouldReportValuesWithNoSpecifiedUpperLimit() {
    final MetricsHistogram histogram =
        MetricsHistogram.create(CATEGORY, metricsSystem, "test", "Test help", 3);

    for (int i = 1; i <= 100; i++) {
      histogram.recordValue(i);
    }
    final Map<List<String>, Object> values =
        metricsSystem
            .streamObservations()
            .filter(ob -> ob.getCategory() == CATEGORY)
            .collect(Collectors.toMap(Observation::getLabels, Observation::getValue));
    assertThat(values)
        .containsOnly(
            entry(key(MetricsHistogram.LABEL_50), 50d),
            entry(key(MetricsHistogram.LABEL_95), 95d),
            entry(key(MetricsHistogram.LABEL_99), 99d),
            entry(key(MetricsHistogram.LABEL_1), 100d));
  }

  @Test
  void shouldReportValuesWithUpperLimit() {
    final MetricsHistogram histogram =
        MetricsHistogram.create(CATEGORY, metricsSystem, "test", "Test help", 2, 80);

    for (int i = 1; i <= 100; i++) {
      histogram.recordValue(i);
    }
    final Map<List<String>, Object> values =
        metricsSystem
            .streamObservations()
            .filter(ob -> ob.getCategory() == CATEGORY)
            .collect(Collectors.toMap(Observation::getLabels, Observation::getValue));
    assertThat(values)
        .containsOnly(
            entry(key(MetricsHistogram.LABEL_50), 50d),
            entry(key(MetricsHistogram.LABEL_95), 80d),
            entry(key(MetricsHistogram.LABEL_99), 80d),
            entry(key(MetricsHistogram.LABEL_1), 80d));
  }

  private static List<String> key(final List<String> labelValues) {
    final List<String> key = new ArrayList<>(MetricsHistogram.LABELS);
    key.addAll(labelValues);
    return key;
  }
}
