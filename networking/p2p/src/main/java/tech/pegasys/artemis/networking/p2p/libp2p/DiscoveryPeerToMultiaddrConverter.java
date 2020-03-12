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

package tech.pegasys.artemis.networking.p2p.libp2p;

import static io.libp2p.crypto.keys.Secp256k1Kt.unmarshalSecp256k1PublicKey;

import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.multiformats.Multiaddr;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryPeer;

public class DiscoveryPeerToMultiaddrConverter {

  public static Multiaddr convertToMultiAddr(final DiscoveryPeer peer) {
    final InetSocketAddress address = peer.getNodeAddress();
    final LibP2PNodeId nodeId = getNodeId(peer);
    final String addrString =
        String.format(
            "/%s/%s/tcp/%d/p2p/%s",
            protocol(address.getAddress()),
            address.getAddress().getHostAddress(),
            address.getPort(),
            nodeId);
    return Multiaddr.fromString(addrString);
  }

  public static LibP2PNodeId getNodeId(final DiscoveryPeer peer) {
    final PubKey pubKey = unmarshalSecp256k1PublicKey(peer.getPublicKey().toArrayUnsafe());
    return new LibP2PNodeId(PeerId.fromPubKey(pubKey));
  }

  private static String protocol(final InetAddress address) {
    return address instanceof Inet6Address ? "ip6" : "ip4";
  }
}
