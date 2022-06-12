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
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.Test;

public class MetricsCountersByIntervalsTest {
  private static final TekuMetricCategory CATEGORY = TekuMetricCategory.BEACON;
  private final ObservableMetricsSystem metricsSystem =
      new PrometheusMetricsSystem(Set.of(CATEGORY), true);

  private static final String COUNTER_NAME = "metric_counter";

  @Test
  void shouldCountWithDefault() {
    final Map<List<String>, List<Long>> eventsAndBoundaries =
        Map.of(
            List.of(), // default, match everything
            List.of(), // one band only [0,∞)
            List.of("label1Val1"),
            List.of(10L, 50L, 80L),
            List.of("label1Val2"),
            List.of(50L),
            List.of("label1Val2", "label2Val"),
            List.of(80L));

    final MetricsCountersByIntervals metric =
        MetricsCountersByIntervals.create(
            CATEGORY,
            metricsSystem,
            COUNTER_NAME,
            "Counter help",
            List.of("label1", "label2"),
            eventsAndBoundaries);

    for (int i = 1; i <= 100; i++) {
      metric.recordValue(i, "label1Val1", "label2UnknownVal");
    }

    for (int i = 1; i <= 100; i++) {
      metric.recordValue(i, "label1Val2", "label2UnknownVal");
    }
    for (int i = 1; i <= 100; i++) {
      metric.recordValue(i, "label1Val2", "label2Val");
    }

    for (int i = 1; i <= 100; i++) {
      metric.recordValue(i, "unknownLabelVal", "unknownLabelVal");
    }

    final Map<List<String>, Object> values =
        metricsSystem
            .streamObservations(CATEGORY)
            .filter(ob -> ob.getMetricName().equals(COUNTER_NAME))
            .collect(Collectors.toMap(Observation::getLabels, Observation::getValue));

    assertThat(values)
        .containsOnly(
            // fallback
            entry(List.of("unknownLabelVal", "unknownLabelVal", "[0,∞)"), 100d),

            // first rule
            entry(List.of("label1Val1", "label2UnknownVal", "[0,10)"), 9d),
            entry(List.of("label1Val1", "label2UnknownVal", "[10,50)"), 40d),
            entry(List.of("label1Val1", "label2UnknownVal", "[50,80)"), 30d),
            entry(List.of("label1Val1", "label2UnknownVal", "[80,∞)"), 21d),
            // second rule
            entry(List.of("label1Val2", "label2UnknownVal", "[0,50)"), 49d),
            entry(List.of("label1Val2", "label2UnknownVal", "[50,∞)"), 51d),

            // third rule
            entry(List.of("label1Val2", "label2Val", "[0,80)"), 79d),
            entry(List.of("label1Val2", "label2Val", "[80,∞)"), 21d));
  }

  @Test
  void shouldNotCountNonMatching() {
    final Map<List<String>, List<Long>> eventsAndBoundaries =
        Map.of(
            List.of("label1Val1"), // match only one value
            List.of()); // one band only [0,∞));

    final MetricsCountersByIntervals metric =
        MetricsCountersByIntervals.create(
            CATEGORY,
            metricsSystem,
            COUNTER_NAME,
            "Counter help",
            List.of("label1", "label2"),
            eventsAndBoundaries);

    metric.recordValue(10, "label1Val1", "label2UnknownVal");
    metric.recordValue(10, "unknownLabelVal", "label2UnknownVal");

    final Map<List<String>, Object> values =
        metricsSystem
            .streamObservations(CATEGORY)
            .filter(ob -> ob.getMetricName().equals(COUNTER_NAME))
            .collect(Collectors.toMap(Observation::getLabels, Observation::getValue));

    assertThat(values).containsOnly(entry(List.of("label1Val1", "label2UnknownVal", "[0,∞)"), 1d));
  }

  @Test
  void shouldInitCounters() {
    final Map<List<String>, List<Long>> eventsAndBoundaries = Map.of(List.of(), List.of());

    final MetricsCountersByIntervals metric =
        MetricsCountersByIntervals.create(
            CATEGORY,
            metricsSystem,
            COUNTER_NAME,
            "Counter help",
            List.of("label1", "label2"),
            eventsAndBoundaries);

    metric.initCounters(List.of("a", "b"));

    final Map<List<String>, Object> values =
        metricsSystem
            .streamObservations(CATEGORY)
            .filter(ob -> ob.getMetricName().equals(COUNTER_NAME))
            .collect(Collectors.toMap(Observation::getLabels, Observation::getValue));

    assertThat(values).containsOnly(entry(List.of("a", "b", "[0,∞)"), 0d));
  }
}
