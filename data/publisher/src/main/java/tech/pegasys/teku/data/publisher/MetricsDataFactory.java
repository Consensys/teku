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

    final Integer cpuCores = null;
    final Integer cpuThreads = getIntegerValue(values, "threads_current");
    final Long cpuNodeSystemSecondsTotal = getLongValue(values, "cpu_seconds_total");
    final Long cpuNodeUserSecondsTotal = null;
    final Long cpuNodeIowaitSecondsTotal = null;
    final Long cpuNodeIdleSecondsTotal = null;
    final Long memoryNodeBytesTotal = getLongValue(values, "memory_bytes_max");
    final Long memoryNodeBytesFree = null;
    final Long memoryNodeBytesCached = null;
    final Long memoryNodeBytesBuffers = null;
    final Long diskNodeBytesTotal = null;
    final Long diskNodeBytesFree = null;
    final Long diskNodeIoSeconds = null;
    final Long diskNodeReadsTotal = null;
    final Long diskNodeWritesTotal = null;
    final Long networkNodeBytesTotalReceive = null;
    final Long networkNodeBytesTotalTransmit = null;
    final Long miscNodeBootTsSeconds = getLongValue(values, "start_time_second");
    final String miscOS = getNormalizedOSVersion();
    return new SystemMetricData(
        PROTOCOL_VERSION,
        timeProvider.getTimeInMillis().longValue(),
        MetricsDataClient.SYSTEM.getDataClient(),
        cpuCores,
        cpuThreads,
        cpuNodeSystemSecondsTotal,
        cpuNodeUserSecondsTotal,
        cpuNodeIowaitSecondsTotal,
        cpuNodeIdleSecondsTotal,
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
        miscNodeBootTsSeconds,
        miscOS);
  }

  private static BaseMetricData extractBeaconNodeData(
      final Map<String, Observation> values, final TimeProvider timeProvider) {

    final Integer networkPeersConnected = getIntegerValue(values, "peer_count");
    final Long diskBeaconchainBytesTotal = null;
    final Long cpuProcessSecondsTotal = getLongValue(values, "cpu_seconds_total");
    final Long memoryProcessBytes = getLongValue(values, "resident_memory_bytes");
    final Boolean syncEth1Connected = null;
    final Boolean syncEth2Synced = null;
    final Long syncBeaconHeadSlot = getLongValue(values, "head_slot");
    final Boolean slasherActive = null;
    final Long networkLibp2PBytesTotalReceive = null;
    final Long networkLibp2PBytesTotalTransmit = null;

    return new BeaconNodeMetricData(
        PROTOCOL_VERSION,
        timeProvider.getTimeInMillis().longValue(),
        MetricsDataClient.BEACON_NODE.getDataClient(),
        cpuProcessSecondsTotal,
        memoryProcessBytes,
        MetricsDataFactory.CLIENT_NAME,
        VersionProvider.IMPLEMENTATION_VERSION.replaceAll("^v", ""),
        diskBeaconchainBytesTotal,
        networkLibp2PBytesTotalReceive,
        networkLibp2PBytesTotalTransmit,
        networkPeersConnected,
        syncEth1Connected,
        syncEth2Synced,
        syncBeaconHeadSlot,
        slasherActive);
  }

  private static BaseMetricData extractValidatorData(
      final Map<String, Observation> values, final TimeProvider timeProvider) {

    final Long cpuProcessSecondsTotal = getLongValue(values, "cpu_seconds_total");
    final Long memoryProcessBytes = getLongValue(values, "resident_memory_bytes");
    final Integer validatorTotal = getIntegerValue(values, "local_validator_count");
    final Integer validatorActive = getIntegerValue(values, "local_validator_counts");
    return new ValidatorMetricData(
        PROTOCOL_VERSION,
        timeProvider.getTimeInMillis().longValue(),
        MetricsDataClient.VALIDATOR.getDataClient(),
        cpuProcessSecondsTotal,
        memoryProcessBytes,
        MetricsDataFactory.CLIENT_NAME,
        VersionProvider.IMPLEMENTATION_VERSION.replaceAll("^v", ""),
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

  private static String getNormalizedOSVersion() {
    String currentVersionInfo = VersionProvider.VERSION;
    if (currentVersionInfo.contains("linux")) return "lin";
    if (currentVersionInfo.contains("windows")) return "win";
    if (currentVersionInfo.contains("osx")) return "mac";
    return "unk";
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
