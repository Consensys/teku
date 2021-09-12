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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;

public class GeneralMetricData extends BaseMetricData {

  private static final String clientName = "Teku";
  private static final int clientBuild = 1;

  public final long cpu_process_seconds_total;
  public final long memory_process_bytes;
  public final String client_name;
  public final String client_version;
  public final int client_build;
  public final int validator_total;
  public final int validator_active;

  @JsonCreator
  public GeneralMetricData(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      @JsonProperty("cpu_process_seconds_total") long cpuProcessSecondsTotal,
      @JsonProperty("memory_process_bytes") long memoryProcessBytes,
      @JsonProperty("client_name") String clientName,
      @JsonProperty("client_version") String clientVersion,
      @JsonProperty("client_build") int clientBuild,
      @JsonProperty("validator_total") int validator_total,
      @JsonProperty("validator_active") int validator_active) {
    super(version, timestamp, process);
    this.cpu_process_seconds_total = cpuProcessSecondsTotal;
    this.memory_process_bytes = memoryProcessBytes;
    this.client_name = clientName;
    this.client_version = clientVersion;
    this.client_build = clientBuild;
    this.validator_total = validator_total;
    this.validator_active = validator_active;
  }

  public GeneralMetricData(
      int version,
      long timestamp,
      String process,
      PrometheusMetricsSystem prometheusMetricsSystem) {
    super(version, timestamp, process);
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
      this.cpu_process_seconds_total =
          ((Double) ((Observation) values.get("cpu_seconds_total")).getValue()).longValue();
    } else {
      this.cpu_process_seconds_total = 0L;
    }
    if (values.containsKey("memory_pool_bytes_used")) {
      this.memory_process_bytes =
          ((Double) ((Observation) values.get("memory_pool_bytes_used")).getValue()).longValue();
    } else {
      this.memory_process_bytes = 0L;
    }
    if (values.containsKey("teku_version")) {
      this.client_version = ((Observation) values.get("teku_version")).getValue().toString();
    } else {
      this.client_version = "";
    }
    if (values.containsKey("current_active_validators")) {
      this.validator_total =
          ((Double) ((Observation) values.get("current_active_validators")).getValue()).intValue();
    } else {
      this.validator_total = 0;
    }
    if (values.containsKey("current_live_validators")) {
      this.validator_active =
          ((Double) ((Observation) values.get("current_live_validators")).getValue()).intValue();
    } else {
      this.validator_active = 0;
    }
    this.client_name = clientName;
    this.client_build = clientBuild;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    GeneralMetricData that = (GeneralMetricData) o;
    return cpu_process_seconds_total == that.cpu_process_seconds_total
        && memory_process_bytes == that.memory_process_bytes
        && client_build == that.client_build
        && validator_total == that.validator_total
        && validator_active == that.validator_active
        && client_name.equals(that.client_name)
        && client_version.equals(that.client_version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        cpu_process_seconds_total,
        memory_process_bytes,
        client_name,
        client_version,
        client_build,
        validator_total,
        validator_active);
  }
}
