/*
 * Copyright Consensys Software Inc., 2022
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

import java.io.Closeable;
import java.io.IOException;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MetricsHistogram {
  private static final double[] DEFAULT_BUCKETS =
      new double[] {
        0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
      };

  private final Histogram histogram;
  private final TimeProvider timeProvider;

  public MetricsHistogram(
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final MetricCategory category,
      final String name,
      final String help,
      final double... buckets) {
    this.histogram =
        metricsSystem.createHistogram(
            category, name, help, buckets.length > 0 ? buckets : DEFAULT_BUCKETS);
    this.timeProvider = timeProvider;
  }

  public static class Timer implements Closeable {
    private final Histogram histogram;
    private final TimeProvider timeProvider;
    private final UInt64 start;

    public Timer(final Histogram histogram, final TimeProvider timeProvider) {
      this.histogram = histogram;
      this.timeProvider = timeProvider;
      start = timeProvider.getTimeInMillis();
    }

    @Override
    public void close() throws IOException {
      histogram.observe(timeProvider.getTimeInMillis().minus(start).doubleValue() / 1000);
    }
  }

  public Timer startTimer() {
    return new Timer(histogram, timeProvider);
  }
}
