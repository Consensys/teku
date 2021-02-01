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

import io.prometheus.client.Collector;
import java.util.Collections;
import java.util.List;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SynchronizedHistogram;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

/**
 * A histogram metric that automatically selects bucket sizes based on simple configuration and the
 * values actually received. Only records values when the metrics system is a {@link
 * PrometheusMetricsSystem}.
 *
 * <p>Backing is an HdrHistogram.
 *
 * @see <a href="https://github.com/HdrHistogram/HdrHistogram">HdrHistogram docs</a>
 */
public class MetricsHistogram {
  static final List<String> LABELS = Collections.singletonList("quantile");
  static final List<String> LABEL_50 = Collections.singletonList("0.5");
  static final List<String> LABEL_95 = Collections.singletonList("0.95");
  static final List<String> LABEL_99 = Collections.singletonList("0.99");
  static final List<String> LABEL_1 = Collections.singletonList("1");

  private final Histogram histogram;

  private MetricsHistogram(final Histogram histogram) {
    this.histogram = histogram;
  }

  /**
   * Create a new histogram metric which autoresizes to fit any values supplied and maintains at
   * least {@code numberOfSignificantValueDigits} of precision.
   *
   * @param category the metrics category
   * @param metricsSystem the metrics system to register with
   * @param name the name of the metric
   * @param help the help text describing the metric
   * @param numberOfSignificantValueDigits the number of digits of precision to preserve
   * @return the new metric
   */
  public static MetricsHistogram create(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String name,
      final String help,
      final int numberOfSignificantValueDigits) {
    final SynchronizedHistogram histogram =
        new SynchronizedHistogram(numberOfSignificantValueDigits);
    return createMetric(category, metricsSystem, name, help, histogram);
  }

  /**
   * Create a new histogram metric with a fixed maximum value, maintaining at least {@code
   * numberOfSignificantValueDigits} of precision.
   *
   * <p>Values above the specified highestTrackableValue will be recorded as being equal to that
   * value.
   *
   * @param category the metrics category
   * @param metricsSystem the metrics system to register with
   * @param name the name of the metric
   * @param help the help text describing the metric
   * @param numberOfSignificantValueDigits the number of digits of precision to preserve
   * @param highestTrackableValue the highest value that can be recorded by this histogram
   * @return the new metric
   */
  public static MetricsHistogram create(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String name,
      final String help,
      final int numberOfSignificantValueDigits,
      final long highestTrackableValue) {
    final SynchronizedHistogram histogram =
        new SynchronizedHistogram(highestTrackableValue, numberOfSignificantValueDigits);
    return createMetric(category, metricsSystem, name, help, histogram);
  }

  private static MetricsHistogram createMetric(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String name,
      final String help,
      final SynchronizedHistogram histogram1) {
    final MetricsHistogram histogram = new MetricsHistogram(histogram1);
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      ((PrometheusMetricsSystem) metricsSystem)
          .addCollector(category, () -> histogram.histogramToCollector(category, name, help));
    }
    return histogram;
  }

  public void recordValue(final long value) {
    if (histogram.isAutoResize()) {
      histogram.recordValue(value);
    } else {
      histogram.recordValue(Math.min(histogram.getHighestTrackableValue(), value));
    }
  }

  private Collector histogramToCollector(
      final MetricCategory metricCategory, final String name, final String help) {
    return new Collector() {
      final String metricName =
          metricCategory.getApplicationPrefix().orElse("") + metricCategory.getName() + "_" + name;

      @Override
      public List<MetricFamilySamples> collect() {
        final List<MetricFamilySamples.Sample> samples =
            List.of(
                new MetricFamilySamples.Sample(
                    metricName, LABELS, LABEL_50, histogram.getValueAtPercentile(50)),
                new MetricFamilySamples.Sample(
                    metricName, LABELS, LABEL_95, histogram.getValueAtPercentile(95d)),
                new MetricFamilySamples.Sample(
                    metricName, LABELS, LABEL_99, histogram.getValueAtPercentile(99d)),
                new MetricFamilySamples.Sample(
                    metricName, LABELS, LABEL_1, histogram.getMaxValueAsDouble()));
        return Collections.singletonList(
            new MetricFamilySamples(metricName, Type.SUMMARY, help, samples));
      }
    };
  }
}
