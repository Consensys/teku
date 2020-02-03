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

package tech.pegasys.artemis.networking.eth2.discovery.network;

import static org.ethereum.beacon.discovery.schema.EnrField.IP_V4;
import static org.ethereum.beacon.discovery.schema.EnrField.UDP_V4;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.crypto.keys.Secp256k1Kt;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.EnrFieldV4;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

public class DiscoveryPeer {

  static Logger logger = LogManager.getLogger();

  private final Integer udpPort;
  private final InetAddress address;
  private final NodeRecord nodeRecord;

  private DiscoveryPeer(
      final NodeRecord nodeRecord, final InetAddress address, final Integer udpPort) {
    this.address = address;
    this.udpPort = udpPort;
    this.nodeRecord = nodeRecord;
  }

  public Bytes getNodeId() {
    return (Bytes) nodeRecord.get(EnrFieldV4.PKEY_SECP256K1);
  }

  public PubKey getPubKey() {
    return Secp256k1Kt.unmarshalSecp256k1PublicKey(getNodeId().toArray());
  }

  public Bytes getDiscoveryId() {
    return nodeRecord.getNodeId();
  }

  public String getNodeIdString() {
    return PeerId.fromPubKey(getPubKey()).toBase58();
  }

  public NodeRecord getNodeRecord() {
    return nodeRecord;
  }

  public Integer getUdpPort() {
    return udpPort;
  }

  public InetAddress getAddress() {
    return address;
  }

  public static DiscoveryPeer fromEnr(final String enr) {
    final NodeRecord node = NodeRecordFactory.DEFAULT.fromBase64(enr);
    return fromNodeRecord(node);
  }

  public static DiscoveryPeer fromNodeRecord(final NodeRecord node) {
    final InetAddress byAddress;
    try {
      // translating the address here means validation before instantiating a DiscoveryPeer
      byAddress = InetAddress.getByAddress(((Bytes) node.get(IP_V4)).toArray());
    } catch (UnknownHostException e) {
      logger.error("Error with address from node record");
      throw new IllegalArgumentException("DiscoveryPeer address not valid");
    }
    final Integer udp = (int) node.get(UDP_V4);
    return new DiscoveryPeer(node, byAddress, udp);
  }
}
