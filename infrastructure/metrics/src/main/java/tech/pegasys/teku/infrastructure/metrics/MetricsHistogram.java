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

import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;

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

  static final double LABEL_0 = 0.0;
  static final double LABEL_50 = 0.5;
  static final double LABEL_95 = 0.95;
  static final double LABEL_99 = 0.99;
  static final double LABEL_1 = 1;

  static final double[] DEFAULT_BUCKETS = {LABEL_0, LABEL_50, LABEL_95, LABEL_99, LABEL_1};

  public static double[] getDefaultBuckets() {
    return DEFAULT_BUCKETS;
  }
}
