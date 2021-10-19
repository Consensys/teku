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
  public final Long disk_beaconchain_bytes_total;
  public final Long network_libp2p_bytes_total_receive;
  public final Long network_libp2p_bytes_total_transmit;
  public final Integer network_peers_connected;
  public final Boolean sync_eth1_connected;
  public final Boolean sync_eth2_synced;
  public final Long sync_beacon_head_slot;
  public final Boolean slasher_active;
  public final String client_name;
  public final String client_version;
  public final Long cpu_process_seconds_total;
  public final Long memory_process_bytes;
  public final Integer validator_total;
  public final Integer validator_active;
  public final Integer cpu_cores;
  public final Integer cpu_threads;
  public final Long cpu_node_system_seconds_total;
  public final Long cpu_node_user_seconds_total;
  public final Long cpu_node_iowait_seconds_total;
  public final Long cpu_node_idle_seconds_total;
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
  public final String misc_os;

  @JsonCreator
  public DeserializedMetricDataObject(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      @JsonProperty("disk_beaconchain_bytes_total") Long diskBeaconchainBytesTotal,
      @JsonProperty("network_libp2p_bytes_total_receive") Long networkLibp2PBytesTotalReceive,
      @JsonProperty("network_libp2p_bytes_total_transmit") Long networkLibp2PBytesTotalTransmit,
      @JsonProperty("network_peers_connected") Integer networkPeersConnected,
      @JsonProperty("sync_eth1_connected") Boolean syncEth1Connected,
      @JsonProperty("sync_eth2_synced") Boolean syncEth2Synced,
      @JsonProperty("sync_beacon_head_slot") Long syncBeaconHeadSlot,
      @JsonProperty("slasher_active") Boolean slasherActive,
      @JsonProperty("client_name") String clientName,
      @JsonProperty("client_version") String clientVersion,
      @JsonProperty("cpu_process_seconds_total") Long cpuProcessSecondsTotal,
      @JsonProperty("memory_process_bytes") Long memoryProcessBytes,
      @JsonProperty("validator_total") Integer validatorTotal,
      @JsonProperty("validator_active") Integer validatorActive,
      @JsonProperty("cpu_cores") Integer cpuCores,
      @JsonProperty("cpu_threads") Integer cpuThreads,
      @JsonProperty("cpu_node_system_seconds_total") Long cpuNodeSystemSecondsTotal,
      @JsonProperty("cpu_node_user_seconds_total") Long cpuNodeUserSecondsTotal,
      @JsonProperty("cpu_node_iowait_seconds_total") Long cpuNodeIowaitSecondsTotal,
      @JsonProperty("cpu_node_idle_seconds_total") Long cpuNodeIdleSecondsTotal,
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
      @JsonProperty("misc_node_boot_ts_seconds") Long miscNodeBootTsSeconds,
      @JsonProperty("misc_os") String miscOS) {
    this.version = version;
    this.timestamp = timestamp;
    this.process = process;
    this.disk_beaconchain_bytes_total = diskBeaconchainBytesTotal;
    this.network_libp2p_bytes_total_receive = networkLibp2PBytesTotalReceive;
    this.network_libp2p_bytes_total_transmit = networkLibp2PBytesTotalTransmit;
    this.network_peers_connected = networkPeersConnected;
    this.sync_eth1_connected = syncEth1Connected;
    this.sync_eth2_synced = syncEth2Synced;
    this.sync_beacon_head_slot = syncBeaconHeadSlot;
    this.slasher_active = slasherActive;
    this.client_name = clientName;
    this.client_version = clientVersion;
    this.cpu_process_seconds_total = cpuProcessSecondsTotal;
    this.memory_process_bytes = memoryProcessBytes;
    this.validator_total = validatorTotal;
    this.validator_active = validatorActive;
    this.cpu_cores = cpuCores;
    this.cpu_threads = cpuThreads;
    this.cpu_node_system_seconds_total = cpuNodeSystemSecondsTotal;
    this.cpu_node_user_seconds_total = cpuNodeUserSecondsTotal;
    this.cpu_node_iowait_seconds_total = cpuNodeIowaitSecondsTotal;
    this.cpu_node_idle_seconds_total = cpuNodeIdleSecondsTotal;
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
        + ", disk_beaconchain_bytes_total="
        + disk_beaconchain_bytes_total
        + ", network_libp2p_bytes_total_receive="
        + network_libp2p_bytes_total_receive
        + ", network_libp2p_bytes_total_transmit="
        + network_libp2p_bytes_total_transmit
        + ", network_peers_connected="
        + network_peers_connected
        + ", sync_eth1_connected="
        + sync_eth1_connected
        + ", sync_eth2_synced="
        + sync_eth2_synced
        + ", sync_beacon_head_slot="
        + sync_beacon_head_slot
        + ", slasher_active="
        + slasher_active
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
        + ", cpu_cores="
        + cpu_cores
        + ", cpu_threads="
        + cpu_threads
        + ", cpu_node_system_seconds_total="
        + cpu_node_system_seconds_total
        + ", cpu_node_user_seconds_total="
        + cpu_node_user_seconds_total
        + ", cpu_node_iowait_seconds_total="
        + cpu_node_iowait_seconds_total
        + ", cpu_node_idle_seconds_total="
        + cpu_node_idle_seconds_total
        + ", memory_node_bytes_total="
        + memory_node_bytes_total
        + ", memory_node_bytes_free="
        + memory_node_bytes_free
        + ", memory_node_bytes_cached="
        + memory_node_bytes_cached
        + ", memory_node_bytes_buffers="
        + memory_node_bytes_buffers
        + ", disk_node_bytes_total="
        + disk_node_bytes_total
        + ", disk_node_bytes_free="
        + disk_node_bytes_free
        + ", disk_node_io_seconds="
        + disk_node_io_seconds
        + ", disk_node_reads_total="
        + disk_node_reads_total
        + ", disk_node_writes_total="
        + disk_node_writes_total
        + ", network_node_bytes_total_receive="
        + network_node_bytes_total_receive
        + ", network_node_bytes_total_transmit="
        + network_node_bytes_total_transmit
        + ", misc_node_boot_ts_seconds="
        + misc_node_boot_ts_seconds
        + ", misc_os='"
        + misc_os
        + '\''
        + '}';
  }
}
