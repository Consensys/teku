/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.data.publisher;

import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class MetricsDataFactory {
  private final MetricsSystem metricsSystem;
  private static final int protocolVersion = 1;

  public MetricsDataFactory(MetricsSystem pms) {
    this.metricsSystem = pms;
  }

  public BaseMetricData getMetricData(MetricsDataClients metricsClient) {
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      if (metricsClient == MetricsDataClients.BEACONCHAIN) {
        return new GeneralMetricData(
            protocolVersion,
            System.currentTimeMillis(),
            "validator",
            (PrometheusMetricsSystem) metricsSystem);
      }
    }
    return new DefaultMetricData(
        protocolVersion, System.currentTimeMillis(), "default", metricsSystem);
  }
}
