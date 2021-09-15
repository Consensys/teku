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

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class MetricsDataFactory {
  private final MetricsSystem metricsSystem;
  private static final int protocolVersion = 1;
  private static final String clientName = "Teku";
  private static final int clientBuild = 1;
  private static final Logger LOG = LogManager.getLogger();

  public MetricsDataFactory(MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
  }

  public BaseMetricData getMetricData(MetricsDataClient metricsClient) {
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      if (metricsClient == MetricsDataClient.VALIDATOR) {
        return extractValidatorData((PrometheusMetricsSystem) metricsSystem);
      }
    } else {
      LOG.error("Prometheus metric system not found.");
    }
    return new DefaultMetricData(
        protocolVersion, System.currentTimeMillis(), "default", metricsSystem);
  }

  private static BaseMetricData extractValidatorData(
      PrometheusMetricsSystem prometheusMetricsSystem) {
    final long cpuProcessSecondsTotal;
    final long memoryProcessBytes;
    final String clientName;
    final String clientVersion;
    final int clientBuild;
    final int validatorTotal;
    final int validatorActive;

    final Map<String, Object> values;
    values =
        prometheusMetricsSystem
            .streamObservations()
            .collect(
                Collectors.toMap(
                    Observation::getMetricName,
                    Function.identity(),
                    (existing, replacement) -> existing));

    if (values.containsKey("cpu_seconds_total")) {
      cpuProcessSecondsTotal =
          ((Double) ((Observation) values.get("cpu_seconds_total")).getValue()).longValue();
    } else {
      cpuProcessSecondsTotal = 0L;
    }
    if (values.containsKey("memory_pool_bytes_used")) {
      memoryProcessBytes =
          ((Double) ((Observation) values.get("memory_pool_bytes_used")).getValue()).longValue();
    } else {
      memoryProcessBytes = 0L;
    }
    if (values.containsKey("teku_version")) {
      clientVersion = ((Observation) values.get("teku_version")).getValue().toString();
    } else {
      clientVersion = "";
    }
    if (values.containsKey("current_active_validators")) {
      validatorTotal =
          ((Double) ((Observation) values.get("current_active_validators")).getValue()).intValue();
    } else {
      validatorTotal = 0;
    }
    if (values.containsKey("current_live_validators")) {
      validatorActive =
          ((Double) ((Observation) values.get("current_live_validators")).getValue()).intValue();
    } else {
      validatorActive = 0;
    }
    clientName = MetricsDataFactory.clientName;
    clientBuild = MetricsDataFactory.clientBuild;
    return new GeneralMetricData(
        protocolVersion,
        System.currentTimeMillis(),
        MetricsDataClient.VALIDATOR.getDataClient(),
        cpuProcessSecondsTotal,
        memoryProcessBytes,
        clientName,
        clientVersion,
        clientBuild,
        validatorTotal,
        validatorActive);
  }
}
