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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidatorMetricData extends BaseMetricData {

  public final Long cpu_process_seconds_total;
  public final Long memory_process_bytes;
  public final String client_name;
  public final String client_version;
  public final Integer validator_total;
  public final Integer validator_active;

  @JsonCreator
  public ValidatorMetricData(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      @JsonProperty("cpu_process_seconds_total") Long cpuProcessSecondsTotal,
      @JsonProperty("memory_process_bytes") Long memoryProcessBytes,
      @JsonProperty("client_name") String clientName,
      @JsonProperty("client_version") String clientVersion,
      @JsonProperty("validator_total") Integer validatorTotal,
      @JsonProperty("validator_active") Integer validatorActive) {
    super(version, timestamp, process);
    this.cpu_process_seconds_total = cpuProcessSecondsTotal;
    this.memory_process_bytes = memoryProcessBytes;
    this.client_name = clientName;
    this.client_version = clientVersion;
    this.validator_total = validatorTotal;
    this.validator_active = validatorActive;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ValidatorMetricData that = (ValidatorMetricData) o;
    return Objects.equals(cpu_process_seconds_total, that.cpu_process_seconds_total)
        && Objects.equals(memory_process_bytes, that.memory_process_bytes)
        && Objects.equals(client_name, that.client_name)
        && Objects.equals(client_version, that.client_version)
        && Objects.equals(validator_total, that.validator_total)
        && Objects.equals(validator_active, that.validator_active);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        cpu_process_seconds_total,
        memory_process_bytes,
        client_name,
        client_version,
        validator_total,
        validator_active);
  }
}
