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

package tech.pegasys.teku.api;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.schema.Metadata;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class NetworkDataProvider {
  private final Eth2Network network;

  public NetworkDataProvider(final Eth2Network network) {
    this.network = network;
  }

  /**
   * get the Ethereum Node Record of the node
   *
   * @return if discovery is in use, returns the Ethereum Node Record (base64).
   */
  public Optional<String> getEnr() {
    return network.getEnr();
  }

  /**
   * Get the current node
   *
   * @return the node id (base58)
   */
  public String getNodeIdAsBase58() {
    return network.getNodeId().toBase58();
  }

  /**
   * Get the list of Peers
   *
   * @return the current list of network peers (base58)
   */
  public List<String> getPeersAsBase58() {
    return network
        .streamPeers()
        .map(Peer::getId)
        .map(NodeId::toBase58)
        .collect(Collectors.toList());
  }

  /**
   * Get the number of Peers
   *
   * @return the the number of peers currently connected to the client
   */
  public long getPeerCount() {
    return network.streamPeers().count();
  }

  /**
   * Get the listen port
   *
   * @return the port this client is listening on
   */
  public int getListenPort() {
    return network.getListenPort();
  }

  public List<String> getListeningAddresses() {
    return List.of(network.getNodeAddress());
  }

  public List<String> getDiscoveryAddresses() {
    Optional<String> discoveryAddressOptional = network.getDiscoveryAddress();
    return discoveryAddressOptional.map(List::of).orElseGet(List::of);
  }

  public Metadata getMetadata() {
    return new Metadata(network.getMetadata());
  }
}
