/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.p2p.network;

import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.artemis.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.artemis.util.async.SafeFuture;

public interface P2PNetwork<T extends Peer> extends GossipNetwork {
  enum State {
    IDLE,
    RUNNING,
    STOPPED
  }

  /**
   * Connects to a Peer using a user supplied address. The address format is specific to the network
   * implementation.
   *
   * @param peer Peer to connect to.
   * @return A future which completes when the connection is establish, containing the newly
   *     connected peer.
   */
  SafeFuture<Peer> connect(String peer);

  /**
   * Connects to a peer identified via discovery.
   *
   * @param peer the peer to connect to.
   * @return A future which completes when the connection is establish, containing the newly
   *     connected peer.
   */
  SafeFuture<Peer> connect(DiscoveryPeer peer);

  long subscribeConnect(PeerConnectedSubscriber<T> subscriber);

  void unsubscribeConnect(long subscriptionId);

  Optional<T> getPeer(NodeId id);

  Stream<T> streamPeers();

  long getPeerCount();

  String getNodeAddress();

  NodeId getNodeId();

  /**
   * starts the p2p network layer
   *
   * @return
   */
  SafeFuture<?> start();

  /** Stops the P2P network layer. */
  void stop();
}
