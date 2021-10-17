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
      Map<String, Observation> values = getStringObjectMap((PrometheusMetricsSystem) metricsSystem);
      metricList.add(extractBeaconNodeData(values, timeProvider));
      metricList.add(extractValidatorData(values, timeProvider));
      metricList.add(extractSystemData(values, timeProvider));
    } else {
      LOG.error("Prometheus metric system not found.");
      metricList.add(
          new MinimalMetricData(
              PROTOCOL_VERSION,
              timeProvider.getTimeInMillis().longValue(),
              MetricsDataClient.MINIMAL.getDataClient(),
              metricsSystem));
    }
    return metricList;
  }

  private BaseMetricData extractSystemData(
      final Map<String, Observation> values, final TimeProvider timeProvider) {

    final Long cpuNodeSystemSecondsTotal = getLongValue(values, "cpu_seconds_total");
    final Long cpuNodeUserSecondsTotal = getLongValue(values, "node_cpu_seconds_total");
    final Long memoryNodeBytesTotal = getLongValue(values, "node_memory_MemTotal_bytes");
    final Long memoryNodeBytesFree = getLongValue(values, "node_memory_MemFree_bytes");
    final Long memoryNodeBytesCached = getLongValue(values, "node_memory_Cached_bytes");
    final Long memoryNodeBytesBuffers = getLongValue(values, "node_memory_Buffers_bytes");
    final Long diskNodeBytesTotal = getLongValue(values, "node_filesystem_size_bytes");
    final Long diskNodeBytesFree = getLongValue(values, "node_filesystem_free_bytes");
    final Long diskNodeIoSeconds = getLongValue(values, "node_disk_io_time_seconds_total");
    final Long diskNodeReadsTotal = getLongValue(values, "node_disk_read_bytes_total");
    final Long diskNodeWritesTotal = getLongValue(values, "node_disk_written_bytes_total");
    final Long networkNodeBytesTotalReceive =
        getLongValue(values, "node_network_receive_bytes_total");
    final Long networkNodeBytesTotalTransmit =
        getLongValue(values, "node_network_transmit_bytes_total");
    final Long miscNodeBootTsSeconds = getLongValue(values, "start_time_second");
    return new SystemMetricData(
        PROTOCOL_VERSION,
        timeProvider.getTimeInMillis().longValue(),
        MetricsDataClient.SYSTEM.getDataClient(),
        cpuNodeSystemSecondsTotal,
        cpuNodeUserSecondsTotal,
        memoryNodeBytesTotal,
        memoryNodeBytesFree,
        memoryNodeBytesCached,
        memoryNodeBytesBuffers,
        diskNodeBytesTotal,
        diskNodeBytesFree,
        diskNodeIoSeconds,
        diskNodeReadsTotal,
        diskNodeWritesTotal,
        networkNodeBytesTotalReceive,
        networkNodeBytesTotalTransmit,
        miscNodeBootTsSeconds);
  }

  private static BaseMetricData extractBeaconNodeData(
      final Map<String, Observation> values, final TimeProvider timeProvider) {
    final Integer networkPeersConnected;

    final Long syncBeaconHeadSlot = getLongValue(values, "head_slot");
    final Long diskBeaconchainBytesTotal = getLongValue(values, "filesystem_size_bytes");
    final Long networkLibp2PBytesTotalReceive = getLongValue(values, "network_receive_bytes_total");
    final Long networkLibp2PBytesTotalTransmit =
        getLongValue(values, "network_transmit_bytes_total");

    networkPeersConnected = getIntegerValue(values, "peer_count");
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
        getStringValue(values, "teku_version"));
  }

  private static BaseMetricData extractValidatorData(
      final Map<String, Observation> values, final TimeProvider timeProvider) {

    final Long cpuProcessSecondsTotal = getLongValue(values, "cpu_seconds_total");
    final Long memoryProcessBytes = getLongValue(values, "memory_pool_bytes_used");
    final Integer validatorTotal = getIntegerValue(values, "current_active_validators");
    final Integer validatorActive = getIntegerValue(values, "current_live_validators");
    return new ValidatorMetricData(
        PROTOCOL_VERSION,
        timeProvider.getTimeInMillis().longValue(),
        MetricsDataClient.VALIDATOR.getDataClient(),
        cpuProcessSecondsTotal,
        memoryProcessBytes,
        MetricsDataFactory.CLIENT_NAME,
        getStringValue(values, "teku_version"),
        validatorTotal,
        validatorActive);
  }

  private static Long getLongValue(Map<String, Observation> values, String key) {
    if (values.containsKey(key)) {
      Double observation = (Double) values.get(key).getValue();
      return observation.longValue();
    }
    return null;
  }

  private static Integer getIntegerValue(Map<String, Observation> values, String key) {
    if (values.containsKey(key)) {
      Double observation = (Double) values.get(key).getValue();
      return observation.intValue();
    }
    return null;
  }

  private static String getStringValue(Map<String, Observation> values, String key) {
    if (values.containsKey(key)) {
      Observation ob = values.get(key);
      if (!ob.getLabels().isEmpty()) {
        return ob.getLabels().get(0);
      }
    }
    return null;
  }

  private static Map<String, Observation> getStringObjectMap(
      PrometheusMetricsSystem prometheusMetricsSystem) {
    Map<String, Observation> values;
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
