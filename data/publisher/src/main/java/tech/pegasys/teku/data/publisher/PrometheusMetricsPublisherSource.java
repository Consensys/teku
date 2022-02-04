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

import java.util.List;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;

public class PrometheusMetricsPublisherSource implements MetricsPublisherSource {
  private long cpuSecondsTotal;
  private long memoryProcessBytes;
  private int validatorsTotal;
  private int validatorsActive;

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

  private void storeObservationIfNeeded(final Observation observation) {
    switch (observation.getMetricName()) {
      case "cpu_seconds_total":
        cpuSecondsTotal = getLongValue(observation.getValue());
        break;
      case "memory_pool_bytes_used":
        addToMemoryPoolBytesUsed((Double) observation.getValue());
        break;
      case "local_validator_counts":
        addToLocalValidators(observation.getLabels(), (Double) observation.getValue());
        break;
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
}
