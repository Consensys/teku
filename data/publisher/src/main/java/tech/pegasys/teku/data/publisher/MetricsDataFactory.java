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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.version.VersionProvider;

public class MetricsDataFactory {
  private final MetricsSystem metricsSystem;
  private static final int PROTOCOL_VERSION = 1;
  private static final String CLIENT_NAME = VersionProvider.CLIENT_IDENTITY;
  private static final Logger LOG = LogManager.getLogger();

  public MetricsDataFactory(MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
  }

  public List<BaseMetricData> getMetricData(final TimeProvider timeProvider) {
    List<BaseMetricData> metricList = new ArrayList<>();
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      Map<String, Object> values = getStringObjectMap((PrometheusMetricsSystem) metricsSystem);
      metricList.add(extractBeaconNodeData(values, timeProvider));
      metricList.add(extractValidatorData(values, timeProvider));
    } else {
      LOG.error("Prometheus metric system not found.");
      metricList.add(
          new MinimalMetricData(
              PROTOCOL_VERSION,
              timeProvider.getTimeInMillis().longValue(),
              "minimal",
              metricsSystem));
    }
    return metricList;
  }

  private static BaseMetricData extractBeaconNodeData(
      final Map<String, Object> values, final TimeProvider timeProvider) {
    final Long diskBeaconchainBytesTotal;
    final Long networkLibp2PBytesTotalReceive;
    final Long networkLibp2PBytesTotalTransmit;
    final Integer networkPeersConnected;
    final Long syncBeaconHeadSlot;

    if (values.containsKey("head_slot")) {
      syncBeaconHeadSlot =
          ((Double) ((Observation) values.get("head_slot")).getValue()).longValue();
    } else {
      syncBeaconHeadSlot = null;
    }
    if (values.containsKey("filesystem_size_bytes")) {
      diskBeaconchainBytesTotal =
          ((Double) ((Observation) values.get("filesystem_size_bytes")).getValue()).longValue();
    } else {
      diskBeaconchainBytesTotal = null;
    }
    if (values.containsKey("network_receive_bytes_total")) {
      networkLibp2PBytesTotalReceive =
          ((Double) ((Observation) values.get("network_receive_bytes_total")).getValue())
              .longValue();
    } else {
      networkLibp2PBytesTotalReceive = null;
    }
    if (values.containsKey("network_transmit_bytes_total")) {
      networkLibp2PBytesTotalTransmit =
          ((Double) ((Observation) values.get("network_transmit_bytes_total")).getValue())
              .longValue();
    } else {
      networkLibp2PBytesTotalTransmit = null;
    }
    if (values.containsKey("peer_count")) {
      networkPeersConnected =
          ((Double) ((Observation) values.get("peer_count")).getValue()).intValue();
    } else {
      networkPeersConnected = null;
    }
    return new BeaconNodeMetricData(
        PROTOCOL_VERSION,
        timeProvider.getTimeInMillis().longValue(),
        MetricsDataClient.BEACON_NODE.getDataClient(),
        diskBeaconchainBytesTotal,
        networkLibp2PBytesTotalReceive,
        networkLibp2PBytesTotalTransmit,
        networkPeersConnected,
        syncBeaconHeadSlot,
        MetricsDataFactory.CLIENT_NAME,
        VersionProvider.IMPLEMENTATION_VERSION);
  }

  private static BaseMetricData extractValidatorData(
      final Map<String, Object> values, final TimeProvider timeProvider) {
    final Long cpuProcessSecondsTotal;
    final Long memoryProcessBytes;
    final Integer validatorTotal;
    final Integer validatorActive;

    if (values.containsKey("cpu_seconds_total")) {
      cpuProcessSecondsTotal =
          ((Double) ((Observation) values.get("cpu_seconds_total")).getValue()).longValue();
    } else {
      cpuProcessSecondsTotal = null;
    }
    if (values.containsKey("memory_pool_bytes_used")) {
      memoryProcessBytes =
          ((Double) ((Observation) values.get("memory_pool_bytes_used")).getValue()).longValue();
    } else {
      memoryProcessBytes = null;
    }
    if (values.containsKey("current_active_validators")) {
      validatorTotal =
          ((Double) ((Observation) values.get("current_active_validators")).getValue()).intValue();
    } else {
      validatorTotal = null;
    }
    if (values.containsKey("current_live_validators")) {
      validatorActive =
          ((Double) ((Observation) values.get("current_live_validators")).getValue()).intValue();
    } else {
      validatorActive = null;
    }
    return new ValidatorMetricData(
        PROTOCOL_VERSION,
        timeProvider.getTimeInMillis().longValue(),
        MetricsDataClient.VALIDATOR.getDataClient(),
        cpuProcessSecondsTotal,
        memoryProcessBytes,
        MetricsDataFactory.CLIENT_NAME,
        VersionProvider.IMPLEMENTATION_VERSION,
        validatorTotal,
        validatorActive);
  }

  private static Map<String, Object> getStringObjectMap(
      PrometheusMetricsSystem prometheusMetricsSystem) {
    Map<String, Object> values;
    values =
        prometheusMetricsSystem
            .streamObservations()
            .collect(
                Collectors.toMap(
                    Observation::getMetricName,
                    Function.identity(),
                    (existing, replacement) -> existing));
    return values;
  }
}
