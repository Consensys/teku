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
import java.util.Objects;

public class ValidatorMetricData extends BaseMetricData {

  public final long cpu_process_seconds_total;
  public final long memory_process_bytes;
  public final String client_name;
  public final String client_version;
  public final int client_build;
  public final int validator_total;
  public final int validator_active;

  @JsonCreator
  public ValidatorMetricData(
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ValidatorMetricData that = (ValidatorMetricData) o;
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
