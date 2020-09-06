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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

public class StubMetricsSystem implements MetricsSystem {

  private final Map<MetricCategory, Map<String, StubCounter>> counters = new ConcurrentHashMap<>();
  private final Map<MetricCategory, Map<String, StubGauge>> gauges = new ConcurrentHashMap<>();

  @Override
  public LabelledMetric<Counter> createLabelledCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return counters
        .computeIfAbsent(category, __ -> new ConcurrentHashMap<>())
        .computeIfAbsent(name, __ -> new StubCounter());
  }

  @Override
  public void createGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final DoubleSupplier valueSupplier) {
    final StubGauge guage = new StubGauge(category, name, help, valueSupplier);
    final Map<String, StubGauge> gaugesInCategory =
        gauges.computeIfAbsent(category, key -> new ConcurrentHashMap<>());

    if (gaugesInCategory.putIfAbsent(name, guage) != null) {
      throw new IllegalArgumentException("Attempting to create two gauges with the same name");
    }
  }

  @Override
  public LabelledMetric<OperationTimer> createLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    throw new UnsupportedOperationException("Timers not supported");
  }

  public StubGauge getGauge(final MetricCategory category, final String name) {
    return Optional.ofNullable(gauges.get(category))
        .map(categoryGauges -> categoryGauges.get(name))
        .orElseThrow(() -> new IllegalArgumentException("Unknown guage: " + category + " " + name));
  }

  public StubCounter getCounter(final MetricCategory category, final String name) {
    return Optional.ofNullable(counters.get(category))
        .map(categoryCounters -> categoryCounters.get(name))
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown counter: " + category + " " + name));
  }
}
