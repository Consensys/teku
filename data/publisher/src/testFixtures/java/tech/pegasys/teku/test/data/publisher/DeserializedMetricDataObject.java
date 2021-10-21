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

package tech.pegasys.teku.test.data.publisher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeserializedMetricDataObject {
  public final int version;
  public final long timestamp;
  public final String process;
  public final Integer network_peers_connected;
  public final Long sync_beacon_head_slot;
  public final String client_name;
  public final String client_version;
  public final Long cpu_process_seconds_total;
  public final Long memory_process_bytes;
  public final Integer validator_total;
  public final Integer validator_active;
  public final Integer cpu_threads;
  public final Long cpu_node_system_seconds_total;
  public final Long memory_node_bytes_total;
  public final Long misc_node_boot_ts_seconds;
  public final String misc_os;

  @JsonCreator
  public DeserializedMetricDataObject(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      @JsonProperty("network_peers_connected") Integer networkPeersConnected,
      @JsonProperty("sync_beacon_head_slot") Long syncBeaconHeadSlot,
      @JsonProperty("client_name") String clientName,
      @JsonProperty("client_version") String clientVersion,
      @JsonProperty("cpu_process_seconds_total") Long cpuProcessSecondsTotal,
      @JsonProperty("memory_process_bytes") Long memoryProcessBytes,
      @JsonProperty("validator_total") Integer validatorTotal,
      @JsonProperty("validator_active") Integer validatorActive,
      @JsonProperty("cpu_threads") Integer cpuThreads,
      @JsonProperty("cpu_node_system_seconds_total") Long cpuNodeSystemSecondsTotal,
      @JsonProperty("memory_node_bytes_total") Long memoryNodeBytesTotal,
      @JsonProperty("misc_node_boot_ts_seconds") Long miscNodeBootTsSeconds,
      @JsonProperty("misc_os") String miscOS) {
    this.version = version;
    this.timestamp = timestamp;
    this.process = process;
    this.network_peers_connected = networkPeersConnected;
    this.sync_beacon_head_slot = syncBeaconHeadSlot;
    this.client_name = clientName;
    this.client_version = clientVersion;
    this.cpu_process_seconds_total = cpuProcessSecondsTotal;
    this.memory_process_bytes = memoryProcessBytes;
    this.validator_total = validatorTotal;
    this.validator_active = validatorActive;
    this.cpu_threads = cpuThreads;
    this.cpu_node_system_seconds_total = cpuNodeSystemSecondsTotal;
    this.memory_node_bytes_total = memoryNodeBytesTotal;
    this.misc_node_boot_ts_seconds = miscNodeBootTsSeconds;
    this.misc_os = miscOS;
  }

  @Override
  public String toString() {
    return "DeserializedMetricDataObject{"
        + "version="
        + version
        + ", timestamp="
        + timestamp
        + ", process='"
        + process
        + '\''
        + ", network_peers_connected="
        + network_peers_connected
        + ", sync_beacon_head_slot="
        + sync_beacon_head_slot
        + ", client_name='"
        + client_name
        + '\''
        + ", client_version='"
        + client_version
        + '\''
        + ", cpu_process_seconds_total="
        + cpu_process_seconds_total
        + ", memory_process_bytes="
        + memory_process_bytes
        + ", validator_total="
        + validator_total
        + ", validator_active="
        + validator_active
        + ", cpu_threads="
        + cpu_threads
        + ", cpu_node_system_seconds_total="
        + cpu_node_system_seconds_total
        + ", memory_node_bytes_total="
        + memory_node_bytes_total
        + ", misc_node_boot_ts_seconds="
        + misc_node_boot_ts_seconds
        + ", misc_os='"
        + misc_os
        + '\''
        + '}';
  }
}
