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

public class MetricsDataFactory {
  private final MetricsSystem metricsSystem;
  private static final int PROTOCOL_VERSION = 1;
  private static final String CLIENT_NAME = "Teku";
  private static final int CLIENT_BUILD = 1;
  private static final Logger LOG = LogManager.getLogger();

  public MetricsDataFactory(MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
  }

  public List<BaseMetricData> getMetricData() {
    List<BaseMetricData> metricList = new ArrayList<>();
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      Map<String, Object> values = getStringObjectMap((PrometheusMetricsSystem) metricsSystem);
      metricList.add(extractBeaconNodeData(values));
      metricList.add(extractValidatorData(values));
    } else {
      LOG.error("Prometheus metric system not found.");
      metricList.add(
          new MinimalMetricData(
              PROTOCOL_VERSION, System.currentTimeMillis(), "minimal", metricsSystem));
    }
    return metricList;
  }

  private static BaseMetricData extractBeaconNodeData(Map<String, Object> values) {
    final long disk_beaconchain_bytes_total;
    final long network_libp2p_bytes_total_receive;
    final long network_libp2p_bytes_total_transmit;
    final int network_peers_connected;
    final long sync_beacon_head_slot;
    final String clientVersion;

    if (values.containsKey("head_slot")) {
      sync_beacon_head_slot =
          ((Double) ((Observation) values.get("head_slot")).getValue()).longValue();
    } else {
      sync_beacon_head_slot = 0;
    }
    if (values.containsKey("teku_version")) {
      clientVersion = ((Observation) values.get("teku_version")).getValue().toString();
    } else {
      clientVersion = "";
    }
    if (values.containsKey("filesystem_size_bytes")) {
      disk_beaconchain_bytes_total =
          ((Double) ((Observation) values.get("filesystem_size_bytes")).getValue()).longValue();
    } else {
      disk_beaconchain_bytes_total = 0;
    }
    if (values.containsKey("network_receive_bytes_total")) {
      network_libp2p_bytes_total_receive =
          ((Double) ((Observation) values.get("network_receive_bytes_total")).getValue())
              .longValue();
    } else {
      network_libp2p_bytes_total_receive = 0;
    }
    if (values.containsKey("network_transmit_bytes_total")) {
      network_libp2p_bytes_total_transmit =
          ((Double) ((Observation) values.get("network_transmit_bytes_total")).getValue())
              .longValue();
    } else {
      network_libp2p_bytes_total_transmit = 0;
    }
    if (values.containsKey("peer_count")) {
      network_peers_connected =
          ((Double) ((Observation) values.get("peer_count")).getValue()).intValue();
    } else {
      network_peers_connected = 0;
    }
    return new BeaconNodeMetricData(
        PROTOCOL_VERSION,
        System.currentTimeMillis(),
        MetricsDataClient.BEACON_NODE.getDataClient(),
        disk_beaconchain_bytes_total,
        network_libp2p_bytes_total_receive,
        network_libp2p_bytes_total_transmit,
        network_peers_connected,
        sync_beacon_head_slot,
        MetricsDataFactory.CLIENT_NAME,
        clientVersion,
        MetricsDataFactory.CLIENT_BUILD);
  }

  private static BaseMetricData extractValidatorData(Map<String, Object> values) {
    final long cpuProcessSecondsTotal;
    final long memoryProcessBytes;
    final String clientVersion;
    final int validatorTotal;
    final int validatorActive;

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
    return new ValidatorMetricData(
        PROTOCOL_VERSION,
        System.currentTimeMillis(),
        MetricsDataClient.VALIDATOR.getDataClient(),
        cpuProcessSecondsTotal,
        memoryProcessBytes,
        MetricsDataFactory.CLIENT_NAME,
        clientVersion,
        MetricsDataFactory.CLIENT_BUILD,
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
