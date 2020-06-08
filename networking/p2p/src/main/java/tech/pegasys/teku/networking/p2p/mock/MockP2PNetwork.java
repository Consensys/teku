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

package tech.pegasys.teku.networking.p2p.mock;

import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.util.async.SafeFuture;

public class MockP2PNetwork<P extends Peer> implements P2PNetwork<P> {
  private final int port = 6000;
  private final NodeId nodeId = new MockNodeId();

  @Override
  public SafeFuture<Peer> connect(PeerAddress peer) {
    return SafeFuture.failedFuture(new UnsupportedOperationException());
  }

  @Override
  public PeerAddress createPeerAddress(final String peerAddress) {
    return new PeerAddress(new MockNodeId(peerAddress.hashCode()));
  }

  @Override
  public PeerAddress createPeerAddress(final DiscoveryPeer discoveryPeer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<P> subscriber) {
    return 0;
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    // Nothing to do
  }

  @Override
  public boolean isConnected(final PeerAddress peerAddress) {
    return false;
  }

  @Override
  public Optional<P> getPeer(final NodeId id) {
    return Optional.empty();
  }

  @Override
  public Stream<P> streamPeers() {
    return Stream.empty();
  }

  @Override
  public int getPeerCount() {
    return 0;
  }

  @Override
  public String getNodeAddress() {
    return "/ip4/127.0.0.1/tcp/" + port + "/p2p/" + nodeId.toBase58();
  }

  @Override
  public NodeId getNodeId() {
    return nodeId;
  }

  @Override
  public Optional<String> getEnr() {
    return Optional.empty();
  }

  @Override
  public Optional<String> getDiscoveryAddress() {
    return Optional.empty();
  }

  @Override
  public int getListenPort() {
    return 0;
  };

  /** Stops the P2P network layer. */
  @Override
  public void stop() {}

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    return new MockTopicChannel();
  }
}
