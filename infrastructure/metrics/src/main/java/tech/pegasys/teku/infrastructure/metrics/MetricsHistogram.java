/*
 * Copyright Consensys Software Inc., 2024
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
import io.prometheus.client.Histogram;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class MetricsHistogram {
  private static final Logger LOG = LogManager.getLogger();
  private static final double[] DEFAULT_BUCKETS =
      new double[] {
        0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
      };

  private final Histogram histogram;

  public MetricsHistogram(
      final MetricsSystem metricsSystem,
      final MetricCategory category,
      final String name,
      final String help,
      final double... buckets) {
    this.histogram =
        Histogram.build()
            .name(category.getName().toLowerCase(Locale.ROOT) + "_" + name)
            .help(help)
            .buckets(buckets.length > 0 ? buckets : DEFAULT_BUCKETS)
            .register();
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      try {
        ((PrometheusMetricsSystem) metricsSystem)
            .addCollector(category, this::histogramToCollector);
      } catch (Exception e) {
        LOG.error("Failed to add collector to PrometheusMetricsSystem", e);
      }
    }
  }

  public record Timer(Histogram.Timer delegate) implements Closeable {
    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  public Timer startTimer() {
    return new Timer(histogram.startTimer());
  }

  protected Collector histogramToCollector() {
    return new Collector() {
      @Override
      public List<MetricFamilySamples> collect() {
        return histogram.collect();
      }
    };
  }
}
