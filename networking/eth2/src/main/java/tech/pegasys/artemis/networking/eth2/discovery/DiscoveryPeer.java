/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.networking.eth2.discovery;

import static org.ethereum.beacon.discovery.schema.EnrField.IP_V4;
import static org.ethereum.beacon.discovery.schema.EnrField.UDP_V4;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

public class DiscoveryPeer {

  static DiscoveryPeer parseEnr(String enr) throws UnknownHostException {
    NodeRecord node = NodeRecordFactory.DEFAULT.fromBase64(enr);
    InetAddress byAddress = InetAddress.getByAddress(((Bytes) node.get(IP_V4)).toArray());
    Bytes nodeId = node.getNodeId();
    Integer udp = (int) node.get(UDP_V4);
    return new DiscoveryPeerBuilder().udp(udp).nodeId(nodeId).address(byAddress).build();
  }

  private Bytes nodeId;
  private Optional<Integer> tcpPort = Optional.empty();
  private Optional<Integer> udpPort = Optional.empty();
  private InetAddress address;

  public Bytes getNodeId() {
    return nodeId;
  }

  public Optional<Integer> getTcpPort() {
    return tcpPort;
  }

  public Optional<Integer> getUdpPort() {
    return udpPort;
  }

  public InetAddress getAddress() {
    return address;
  }

  private DiscoveryPeer() {}

  void setNodeId(Bytes nodeId) {
    this.nodeId = nodeId;
  }

  void setTcpPort(Optional<Integer> tcpPort) {
    this.tcpPort = tcpPort;
  }

  void setUdpPort(Optional<Integer> udpPort) {
    this.udpPort = udpPort;
  }

  void setAddress(InetAddress address) {
    this.address = address;
  }

  static class DiscoveryPeerBuilder {

    InetAddress address;
    Optional<Integer> tcpPort = Optional.empty();
    Optional<Integer> udpPort = Optional.empty();
    Bytes nodeId;

    public DiscoveryPeerBuilder tcp(Integer tcpPort) {
      this.tcpPort = Optional.of(tcpPort);
      return this;
    }

    public DiscoveryPeerBuilder udp(Integer udpPort) {
      this.udpPort = Optional.of(udpPort);
      return this;
    }

    public DiscoveryPeerBuilder nodeId(Bytes nodeId) {
      this.nodeId = nodeId;
      return this;
    }

    public DiscoveryPeerBuilder address(InetAddress address) {
      this.address = address;
      return this;
    }

    public DiscoveryPeer build() {
      DiscoveryPeer peer = new DiscoveryPeer();
      peer.setTcpPort(tcpPort);
      peer.setUdpPort(udpPort);
      peer.setAddress(address);
      peer.setNodeId(nodeId);
      return peer;
    }
  }
}
