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
public class BeaconNodeMetricData extends BaseMetricData {

  public final Long disk_beaconchain_bytes_total;
  public final Long network_libp2p_bytes_total_receive;
  public final Long network_libp2p_bytes_total_transmit;
  public final Integer network_peers_connected;
  public final Long sync_beacon_head_slot;
  public final String client_name;
  public final String client_version;

  @JsonCreator
  public BeaconNodeMetricData(
      @JsonProperty("version") int version,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("process") String process,
      @JsonProperty("disk_beaconchain_bytes_total") Long disk_beaconchain_bytes_total,
      @JsonProperty("network_libp2p_bytes_total_receive") Long network_libp2p_bytes_total_receive,
      @JsonProperty("network_libp2p_bytes_total_transmit") Long network_libp2p_bytes_total_transmit,
      @JsonProperty("network_peers_connected") Integer network_peers_connected,
      @JsonProperty("sync_beacon_head_slot") Long sync_beacon_head_slot,
      @JsonProperty("client_name") String clientName,
      @JsonProperty("client_version") String clientVersion) {
    super(version, timestamp, process);
    this.disk_beaconchain_bytes_total = disk_beaconchain_bytes_total;
    this.network_libp2p_bytes_total_receive = network_libp2p_bytes_total_receive;
    this.network_libp2p_bytes_total_transmit = network_libp2p_bytes_total_transmit;
    this.network_peers_connected = network_peers_connected;
    this.sync_beacon_head_slot = sync_beacon_head_slot;
    this.client_name = clientName;
    this.client_version = clientVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    BeaconNodeMetricData that = (BeaconNodeMetricData) o;
    return Objects.equals(disk_beaconchain_bytes_total, that.disk_beaconchain_bytes_total)
        && Objects.equals(
            network_libp2p_bytes_total_receive, that.network_libp2p_bytes_total_receive)
        && Objects.equals(
            network_libp2p_bytes_total_transmit, that.network_libp2p_bytes_total_transmit)
        && Objects.equals(network_peers_connected, that.network_peers_connected)
        && Objects.equals(sync_beacon_head_slot, that.sync_beacon_head_slot)
        && Objects.equals(client_name, that.client_name)
        && Objects.equals(client_version, that.client_version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        disk_beaconchain_bytes_total,
        network_libp2p_bytes_total_receive,
        network_libp2p_bytes_total_transmit,
        network_peers_connected,
        sync_beacon_head_slot,
        client_name,
        client_version);
  }
}
