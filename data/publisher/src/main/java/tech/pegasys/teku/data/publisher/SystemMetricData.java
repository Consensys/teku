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

  public final Long cpu_node_system_seconds_total;
  public final Long cpu_node_user_seconds_total;

  public final Long memory_node_bytes_total;
  public final Long memory_node_bytes_free;
  public final Long memory_node_bytes_cached;
  public final Long memory_node_bytes_buffers;

  public final Long disk_node_bytes_total;
  public final Long disk_node_bytes_free;
  public final Long disk_node_io_seconds;
  public final Long disk_node_reads_total;
  public final Long disk_node_writes_total;

  public final Long network_node_bytes_total_receive;
  public final Long network_node_bytes_total_transmit;
  public final Long misc_node_boot_ts_seconds;

  public SystemMetricData(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      @JsonProperty("cpu_node_system_seconds_total") Long cpuNodeSystemSecondsTotal,
      @JsonProperty("cpu_node_user_seconds_total") Long cpuNodeUserSecondsTotal,
      @JsonProperty("memory_node_bytes_total") Long memoryNodeBytesTotal,
      @JsonProperty("memory_node_bytes_free") Long memoryNodeBytesFree,
      @JsonProperty("memory_node_bytes_cached") Long memoryNodeBytesCached,
      @JsonProperty("memory_node_bytes_buffers") Long memoryNodeBytesBuffers,
      @JsonProperty("disk_node_bytes_total") Long diskNodeBytesTotal,
      @JsonProperty("disk_node_bytes_free") Long diskNodeBytesFree,
      @JsonProperty("disk_node_io_seconds") Long diskNodeIoSeconds,
      @JsonProperty("disk_node_reads_total") Long diskNodeReadsTotal,
      @JsonProperty("disk_node_writes_total") Long diskNodeWritesTotal,
      @JsonProperty("network_node_bytes_total_receive") Long networkNodeBytesTotalReceive,
      @JsonProperty("network_node_bytes_total_transmit") Long networkNodeBytesTotalTransmit,
      @JsonProperty("misc_node_boot_ts_seconds") Long miscNodeBootTsSeconds) {
    super(version, timestamp, process);
    this.cpu_node_system_seconds_total = cpuNodeSystemSecondsTotal;
    this.cpu_node_user_seconds_total = cpuNodeUserSecondsTotal;
    this.memory_node_bytes_total = memoryNodeBytesTotal;
    this.memory_node_bytes_free = memoryNodeBytesFree;
    this.memory_node_bytes_cached = memoryNodeBytesCached;
    this.memory_node_bytes_buffers = memoryNodeBytesBuffers;
    this.disk_node_bytes_total = diskNodeBytesTotal;
    this.disk_node_bytes_free = diskNodeBytesFree;
    this.disk_node_io_seconds = diskNodeIoSeconds;
    this.disk_node_reads_total = diskNodeReadsTotal;
    this.disk_node_writes_total = diskNodeWritesTotal;
    this.network_node_bytes_total_receive = networkNodeBytesTotalReceive;
    this.network_node_bytes_total_transmit = networkNodeBytesTotalTransmit;
    this.misc_node_boot_ts_seconds = miscNodeBootTsSeconds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SystemMetricData that = (SystemMetricData) o;
    return Objects.equals(cpu_node_system_seconds_total, that.cpu_node_system_seconds_total)
        && Objects.equals(cpu_node_user_seconds_total, that.cpu_node_user_seconds_total)
        && Objects.equals(memory_node_bytes_total, that.memory_node_bytes_total)
        && Objects.equals(memory_node_bytes_free, that.memory_node_bytes_free)
        && Objects.equals(memory_node_bytes_cached, that.memory_node_bytes_cached)
        && Objects.equals(memory_node_bytes_buffers, that.memory_node_bytes_buffers)
        && Objects.equals(disk_node_bytes_total, that.disk_node_bytes_total)
        && Objects.equals(disk_node_bytes_free, that.disk_node_bytes_free)
        && Objects.equals(disk_node_io_seconds, that.disk_node_io_seconds)
        && Objects.equals(disk_node_reads_total, that.disk_node_reads_total)
        && Objects.equals(disk_node_writes_total, that.disk_node_writes_total)
        && Objects.equals(network_node_bytes_total_receive, that.network_node_bytes_total_receive)
        && Objects.equals(network_node_bytes_total_transmit, that.network_node_bytes_total_transmit)
        && Objects.equals(misc_node_boot_ts_seconds, that.misc_node_boot_ts_seconds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        cpu_node_system_seconds_total,
        cpu_node_user_seconds_total,
        memory_node_bytes_total,
        memory_node_bytes_free,
        memory_node_bytes_cached,
        memory_node_bytes_buffers,
        disk_node_bytes_total,
        disk_node_bytes_free,
        disk_node_io_seconds,
        disk_node_reads_total,
        disk_node_writes_total,
        network_node_bytes_total_receive,
        network_node_bytes_total_transmit,
        misc_node_boot_ts_seconds);
  }
}
