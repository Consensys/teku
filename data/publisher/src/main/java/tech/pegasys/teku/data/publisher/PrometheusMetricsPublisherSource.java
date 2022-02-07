/*
 * Copyright 2022 ConsenSys AG.
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

import static org.hyperledger.besu.metrics.StandardMetricCategory.JVM;
import static org.hyperledger.besu.metrics.StandardMetricCategory.PROCESS;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.EXECUTOR;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.VALIDATOR;

import java.util.List;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class PrometheusMetricsPublisherSource implements MetricsPublisherSource {
  private long cpuSecondsTotal;
  private long memoryProcessBytes;
  private long headSlot;
  private long gossipBytesTotalSent;
  private long gossipBytesTotalReceived;
  private int validatorsTotal;
  private int validatorsActive;
  private int peerCount;
  private boolean isBeaconNodePresent;
  private boolean isEth2Synced;
  private boolean isEth1Connected;

  public PrometheusMetricsPublisherSource(final PrometheusMetricsSystem metricsSystem) {
    metricsSystem.streamObservations().forEach(this::storeObservationIfNeeded);
  }

  @Override
  public long getCpuSecondsTotal() {
    return cpuSecondsTotal;
  }

  @Override
  public long getMemoryProcessBytes() {
    return memoryProcessBytes;
  }

  @Override
  public int getValidatorsTotal() {
    return validatorsTotal;
  }

  @Override
  public int getValidatorsActive() {
    return validatorsActive;
  }

  @Override
  public long getHeadSlot() {
    return headSlot;
  }

  @Override
  public boolean isValidatorPresent() {
    return validatorsTotal > 0;
  }

  @Override
  public boolean isBeaconNodePresent() {
    return isBeaconNodePresent;
  }

  @Override
  public boolean isEth2Synced() {
    return isEth2Synced;
  }

  @Override
  public boolean isEth1Connected() {
    return isEth1Connected;
  }

  @Override
  public int getPeerCount() {
    return peerCount;
  }

  @Override
  public long getGossipBytesTotalSent() {
    return gossipBytesTotalSent;
  }

  @Override
  public long getGossipBytesTotalReceived() {
    return gossipBytesTotalReceived;
  }

  private void storeObservationIfNeeded(final Observation observation) {
    MetricCategory category = observation.getCategory();
    if (category.equals(EXECUTOR)) {
      readExecutorCategoryItem(observation);
    } else if (category.equals(PROCESS)) {
      readProcessCategoryItem(observation);
    } else if (category.equals(VALIDATOR)) {
      readValidatorCategoryItem(observation);
    } else if (category.equals(JVM)) {
      readJvmCategoryItem(observation);
    } else if (category.equals(BEACON)) {
      readBeaconCategoryItem(observation);
    } else if (category.equals(LIBP2P)) {
      readLibP2pCategoryItem(observation);
    }
  }

  private void readLibP2pCategoryItem(final Observation observation) {
    if (observation.getMetricName().equals("gossip_bytes_total")) {
      if (observation.getLabels().contains("received_bytes")) {
        gossipBytesTotalReceived = getLongValue(observation.getValue());
      } else if (observation.getLabels().contains("sent_bytes")) {
        gossipBytesTotalSent = getLongValue(observation.getValue());
      }
    }
  }

  private void readBeaconCategoryItem(final Observation observation) {
    isBeaconNodePresent = true;
    switch (observation.getMetricName()) {
      case "head_slot":
        headSlot = getLongValue(observation.getValue());
        break;
      case "eth1_request_queue_size":
        isEth1Connected = true;
        break;
      case "peer_count":
        peerCount = getIntValue(observation.getValue());
        break;
    }
  }

  private void readExecutorCategoryItem(final Observation observation) {
    if ("sync_thread_active_count".equals(observation.getMetricName())) {
      isEth2Synced = getLongValue(observation.getValue()) == 0;
    }
  }

  private void readProcessCategoryItem(final Observation observation) {
    if ("cpu_seconds_total".equals(observation.getMetricName())) {
      cpuSecondsTotal = getLongValue(observation.getValue());
    }
  }

  private void readJvmCategoryItem(final Observation observation) {
    if ("memory_pool_bytes_used".equals(observation.getMetricName())) {
      addToMemoryPoolBytesUsed((Double) observation.getValue());
    }
  }

  private void readValidatorCategoryItem(final Observation observation) {
    if ("local_validator_counts".equals(observation.getMetricName())) {
      addToLocalValidators(observation.getLabels(), (Double) observation.getValue());
    }
  }

  private void addToLocalValidators(final List<String> labels, final Double value) {
    if (labels.contains("active_ongoing")) {
      validatorsActive = value.intValue();
    }
    validatorsTotal += value.intValue();
  }

  private void addToMemoryPoolBytesUsed(final Double observedValue) {
    memoryProcessBytes += observedValue.longValue();
  }

  private long getLongValue(final Object value) {
    Double current = (Double) value;
    return current.longValue();
  }

  private int getIntValue(final Object value) {
    Double current = (Double) value;
    return current.intValue();
  }
}
