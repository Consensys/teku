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

package tech.pegasys.artemis.api;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;

public class NetworkDataProvider {
  private final P2PNetwork<?> p2pNetwork;

  public NetworkDataProvider(final P2PNetwork<?> p2pNetwork) {
    this.p2pNetwork = p2pNetwork;
  }

  /**
   * get the Ethereum Node Record of the node
   *
   * @return if discovery is in use, returns the Ethereum Node Record (base64).
   */
  public Optional<String> getEnr() {
    return p2pNetwork.getEnr();
  }

  /**
   * Get the current node
   *
   * @return the node id (base58)
   */
  public String getNodeIdAsBase58() {
    return p2pNetwork.getNodeId().toBase58();
  }

  /**
   * Get the list of Peers
   *
   * @return the current list of network peers (base58)
   */
  public List<String> getPeersAsBase58() {
    return p2pNetwork
        .streamPeers()
        .map(Peer::getId)
        .map(NodeId::toBase58)
        .collect(Collectors.toList());
  }

  P2PNetwork<?> getP2pNetwork() {
    return p2pNetwork;
  }
}
