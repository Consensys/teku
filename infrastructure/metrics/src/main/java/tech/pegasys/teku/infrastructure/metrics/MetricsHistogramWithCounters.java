/*
 * Copyright 2022 ConsenSys AG.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SynchronizedHistogram;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MetricsHistogramWithCounters extends MetricsHistogram {
  private final List<UInt64> sortedBoundaries;
  private final Map<UInt64, String> boundariesToIntervalLabels;
  private final Map<String, LabelledMetric<Counter>> intervalLabelsToMetrics;

  public MetricsHistogramWithCounters(
      final Histogram histogram,
      final List<UInt64> sortedBoundaries,
      final Map<UInt64, String> boundariesToIntervalLabels,
      final Map<String, LabelledMetric<Counter>> intervalLabelsToMetrics) {
    super(histogram);
    this.boundariesToIntervalLabels = boundariesToIntervalLabels;
    this.intervalLabelsToMetrics = intervalLabelsToMetrics;
    this.sortedBoundaries = sortedBoundaries;
  }

  public static MetricsHistogramWithCounters create(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String histogramName,
      final String histogramHelp,
      final String counterName,
      final String counterHelp,
      final int numberOfSignificantValueDigits,
      final List<UInt64> boundaries) {
    final List<UInt64> sortedBoundaries =
        boundaries.stream().sorted().collect(Collectors.toUnmodifiableList());
    final SynchronizedHistogram histogram =
        new SynchronizedHistogram(numberOfSignificantValueDigits);
    final Map<UInt64, String> boundariesToIntervalLabels =
        boundariesToIntervalLabels(sortedBoundaries);
    final Map<String, LabelledMetric<Counter>> boundariesToMetrics =
        createCounterMetricsForBoundaries(
            boundariesToIntervalLabels.values(), category, counterName, counterHelp, metricsSystem);
    return createMetric(
        category,
        metricsSystem,
        histogramName,
        histogramHelp,
        histogram,
        sortedBoundaries,
        boundariesToIntervalLabels,
        boundariesToMetrics);
  }

  private static MetricsHistogramWithCounters createMetric(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String name,
      final String help,
      final SynchronizedHistogram histogram1,
      final List<UInt64> sortedBoundaries,
      final Map<UInt64, String> boundariesToIntervalLabels,
      final Map<String, LabelledMetric<Counter>> intervalLabelsToMetrics) {

    final MetricsHistogramWithCounters histogram =
        new MetricsHistogramWithCounters(
            histogram1, sortedBoundaries, boundariesToIntervalLabels, intervalLabelsToMetrics);

    if (metricsSystem instanceof PrometheusMetricsSystem) {
      ((PrometheusMetricsSystem) metricsSystem)
          .addCollector(category, () -> histogram.histogramToCollector(category, name, help));
    }
    return histogram;
  }

  @Override
  public void recordValue(final long value) {
    super.recordValue(value);
    updateCounterMetric(UInt64.valueOf(value));
  }

  public void recordValue(final UInt64 value) {
    super.recordValue(value.longValue());
    updateCounterMetric(value);
  }

  private void updateCounterMetric(final UInt64 value) {
    String intervalLabel = resolveValueToIntervalLabel(value);
    intervalLabelsToMetrics.get(intervalLabel).labels(intervalLabel).inc();
  }

  private String resolveValueToIntervalLabel(final UInt64 value) {
    final int lastInterval = sortedBoundaries.size() - 1;
    for (int i = 0; i < lastInterval; i++) {
      final UInt64 currentBoundary = sortedBoundaries.get(i);
      if (value.isLessThan(currentBoundary)) {
        return boundariesToIntervalLabels.get(currentBoundary);
      }
    }
    return boundariesToIntervalLabels.get(sortedBoundaries.get(lastInterval));
  }

  protected static Map<UInt64, String> boundariesToIntervalLabels(
      final List<UInt64> sortedBoundaries) {
    UInt64 previous = UInt64.ZERO;
    final Map<UInt64, String> boundariesToLabels = new HashMap<>();

    for (UInt64 boundary : sortedBoundaries) {
      boundariesToLabels.put(boundary, "[" + previous + "," + boundary + ")");
      previous = boundary;
    }
    boundariesToLabels.put(previous, "[" + previous + ",∞)");
    return boundariesToLabels;
  }

  protected static Map<String, LabelledMetric<Counter>> createCounterMetricsForBoundaries(
      final Collection<String> intervalLabels,
      final MetricCategory category,
      final String name,
      final String help,
      final MetricsSystem metricsSystem) {
    return intervalLabels.stream()
        .collect(
            Collectors.<String, String, LabelledMetric<Counter>>toMap(
                intervalLabel -> intervalLabel,
                __ -> metricsSystem.createLabelledCounter(category, name, help, "interval")));
  }
}
