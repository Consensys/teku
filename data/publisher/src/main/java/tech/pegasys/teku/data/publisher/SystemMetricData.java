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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemMetricData extends BaseMetricData {

  public final Integer cpu_threads;
  public final Long cpu_node_system_seconds_total;
  public final Long memory_node_bytes_total;
  public final Long misc_node_boot_ts_seconds;
  public final String misc_os;

  public SystemMetricData(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      @JsonProperty("cpu_threads") Integer cpuThreads,
      @JsonProperty("cpu_node_system_seconds_total") Long cpuNodeSystemSecondsTotal,
      @JsonProperty("memory_node_bytes_total") Long memoryNodeBytesTotal,
      @JsonProperty("misc_node_boot_ts_seconds") Long miscNodeBootTsSeconds,
      @JsonProperty("misc_os") String miscOS) {
    super(version, timestamp, process);
    this.cpu_threads = cpuThreads;
    this.cpu_node_system_seconds_total = cpuNodeSystemSecondsTotal;
    this.memory_node_bytes_total = memoryNodeBytesTotal;
    this.misc_node_boot_ts_seconds = miscNodeBootTsSeconds;
    this.misc_os = miscOS;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SystemMetricData that = (SystemMetricData) o;
    return Objects.equals(cpu_threads, that.cpu_threads)
        && Objects.equals(cpu_node_system_seconds_total, that.cpu_node_system_seconds_total)
        && Objects.equals(memory_node_bytes_total, that.memory_node_bytes_total)
        && Objects.equals(misc_node_boot_ts_seconds, that.misc_node_boot_ts_seconds)
        && Objects.equals(misc_os, that.misc_os);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        cpu_threads,
        cpu_node_system_seconds_total,
        memory_node_bytes_total,
        misc_node_boot_ts_seconds,
        misc_os);
  }
}
