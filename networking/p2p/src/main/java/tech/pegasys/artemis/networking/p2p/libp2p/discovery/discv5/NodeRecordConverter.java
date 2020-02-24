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

package tech.pegasys.artemis.networking.p2p.libp2p.discovery.discv5;

import io.libp2p.core.multiformats.Multiaddr;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import tech.pegasys.artemis.networking.p2p.libp2p.discovery.DiscoveryPeer;

public class NodeRecordConverter {
  private static final Logger LOG = LogManager.getLogger();

  static Optional<DiscoveryPeer> convertToDiscoveryPeer(final NodeRecord nodeRecord) {
    final DiscoveryNodeId nodeId = new DiscoveryNodeId(nodeRecord.getNodeId());
    final String protocol;
    final Bytes ipAddress;
    final int port;
    if (nodeRecord.containsKey(EnrField.IP_V4) && nodeRecord.containsKey(EnrField.TCP_V4)) {
      protocol = "ip4";
      ipAddress = (Bytes) nodeRecord.get(EnrField.IP_V4);
      port = (int) nodeRecord.get(EnrField.TCP_V4);
    } else if (nodeRecord.containsKey(EnrField.IP_V6) && nodeRecord.containsKey(EnrField.TCP_V6)) {
      protocol = "ip6";
      ipAddress = (Bytes) nodeRecord.get(EnrField.IP_V6);
      port = (int) nodeRecord.get(EnrField.TCP_V6);
    } else {
      LOG.trace(
          "Unable to convert ENR record to MultiAddr: {}. NodeId: {}",
          nodeRecord::asEnr,
          nodeRecord::getNodeId);
      return Optional.empty();
    }

    try {
      final String addrString =
          String.format(
              "/%s/%s/tcp/%d/p2p/%s",
              protocol, ipAddressToString(ipAddress), port, nodeId.toBase58());
      return Optional.of(new DiscoveryPeer(nodeId, Multiaddr.fromString(addrString)));
    } catch (final UnknownHostException e) {
      LOG.trace("Unable to resolve host: {}", ipAddress);
      return Optional.empty();
    }
  }

  private static String ipAddressToString(final Bytes address) throws UnknownHostException {
    return InetAddress.getByAddress(address.toArrayUnsafe()).getHostAddress();
  }
}
