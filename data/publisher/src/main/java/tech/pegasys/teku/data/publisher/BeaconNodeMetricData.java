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

public class BeaconNodeMetricData extends BaseMetricData {

  public final long disk_beaconchain_bytes_total;
  public final long network_libp2p_bytes_total_receive;
  public final long network_libp2p_bytes_total_transmit;
  public final int network_peers_connected;
  public final long sync_beacon_head_slot;
  public final String client_name;
  public final String client_version;
  public final int client_build;

  @JsonCreator
  public BeaconNodeMetricData(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      @JsonProperty("disk_beaconchain_bytes_total") long disk_beaconchain_bytes_total,
      @JsonProperty("network_libp2p_bytes_total_receive") long network_libp2p_bytes_total_receive,
      @JsonProperty("network_libp2p_bytes_total_transmit") long network_libp2p_bytes_total_transmit,
      @JsonProperty("network_peers_connected") int network_peers_connected,
      @JsonProperty("sync_beacon_head_slot") long sync_beacon_head_slot,
      @JsonProperty("client_name") String clientName,
      @JsonProperty("client_version") String clientVersion,
      @JsonProperty("client_build") int clientBuild) {
    super(version, timestamp, process);
    this.disk_beaconchain_bytes_total = disk_beaconchain_bytes_total;
    this.network_libp2p_bytes_total_receive = network_libp2p_bytes_total_receive;
    this.network_libp2p_bytes_total_transmit = network_libp2p_bytes_total_transmit;
    this.network_peers_connected = network_peers_connected;
    this.sync_beacon_head_slot = sync_beacon_head_slot;
    this.client_name = clientName;
    this.client_version = clientVersion;
    this.client_build = clientBuild;
  }
}
