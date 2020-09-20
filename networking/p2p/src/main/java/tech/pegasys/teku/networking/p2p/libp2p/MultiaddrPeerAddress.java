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

package tech.pegasys.teku.networking.p2p.libp2p;

import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multiformats.Protocol;
import java.util.Objects;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class MultiaddrPeerAddress extends PeerAddress {

  private final Multiaddr multiaddr;

  MultiaddrPeerAddress(final NodeId nodeId, final Multiaddr multiaddr) {
    super(nodeId);
    this.multiaddr = multiaddr;
  }

  @Override
  public String toExternalForm() {
    return multiaddr.toString();
  }

  public static MultiaddrPeerAddress fromAddress(final String address) {
    final Multiaddr multiaddr = Multiaddr.fromString(address);
    return fromMultiaddr(multiaddr);
  }

  public static MultiaddrPeerAddress fromDiscoveryPeer(final DiscoveryPeer discoveryPeer) {
    final Multiaddr multiaddr = MultiaddrUtil.fromDiscoveryPeer(discoveryPeer);
    return fromMultiaddr(multiaddr);
  }

  private static MultiaddrPeerAddress fromMultiaddr(final Multiaddr multiaddr) {
    final String p2pComponent = multiaddr.getStringComponent(Protocol.P2P);
    if (p2pComponent == null) {
      throw new IllegalArgumentException("No peer ID present in multiaddr: " + multiaddr);
    }
    final LibP2PNodeId nodeId = new LibP2PNodeId(PeerId.fromBase58(p2pComponent));
    return new MultiaddrPeerAddress(nodeId, multiaddr);
  }

  public Multiaddr getMultiaddr() {
    return multiaddr;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final MultiaddrPeerAddress that = (MultiaddrPeerAddress) o;
    return Objects.equals(multiaddr, that.multiaddr);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), multiaddr);
  }
}
