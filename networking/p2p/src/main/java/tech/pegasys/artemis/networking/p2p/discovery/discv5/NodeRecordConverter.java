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

package tech.pegasys.artemis.networking.p2p.discovery.discv5;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryPeer;

public class NodeRecordConverter {
  private static final Logger LOG = LogManager.getLogger();

  static Optional<DiscoveryPeer> convertToDiscoveryPeer(final NodeRecord nodeRecord) {
    return addressFromFields(nodeRecord, EnrField.IP_V4, EnrField.TCP_V4)
        .or(() -> addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.TCP_V6))
        .or(() -> addressFromFields(nodeRecord, EnrField.IP_V6, EnrField.TCP_V4))
        .map(address -> socketAddressToDiscoveryPeer(nodeRecord, address));
  }

  private static DiscoveryPeer socketAddressToDiscoveryPeer(
      final NodeRecord nodeRecord, final InetSocketAddress address) {
    return new DiscoveryPeer(((Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1)), address);
  }

  private static Optional<InetSocketAddress> addressFromFields(
      final NodeRecord nodeRecord, final String ipField, final String portField) {
    if (!nodeRecord.containsKey(ipField) || !nodeRecord.containsKey(portField)) {
      return Optional.empty();
    }
    final Bytes ipBytes = (Bytes) nodeRecord.get(ipField);
    final int port = (int) nodeRecord.get(portField);
    try {
      return Optional.of(new InetSocketAddress(getInetAddress(ipBytes), port));
    } catch (final UnknownHostException e) {
      LOG.trace("Unable to resolve host: {}", ipBytes);
      return Optional.empty();
    }
  }

  private static InetAddress getInetAddress(final Bytes address) throws UnknownHostException {
    return InetAddress.getByAddress(address.toArrayUnsafe());
  }
}
