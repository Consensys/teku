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

package tech.pegasys.teku.networking.p2p.network;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public abstract class DelegatingP2PNetwork<T extends Peer> implements P2PNetwork<T> {
  private final P2PNetwork<?> network;

  protected DelegatingP2PNetwork(final P2PNetwork<?> network) {
    this.network = network;
  }

  @Override
  public SafeFuture<Peer> connect(final PeerAddress peer) {
    return network.connect(peer);
  }

  @Override
  public PeerAddress createPeerAddress(final DiscoveryPeer discoveryPeer) {
    return network.createPeerAddress(discoveryPeer);
  }

  @Override
  public NodeId parseNodeId(final String nodeId) {
    return network.parseNodeId(nodeId);
  }

  @Override
  public boolean isConnected(final PeerAddress peerAddress) {
    return network.isConnected(peerAddress);
  }

  @Override
  public Bytes getPrivateKey() {
    return network.getPrivateKey();
  }

  @Override
  public PeerAddress createPeerAddress(final String peerAddress) {
    return network.createPeerAddress(peerAddress);
  }

  @Override
  public int getPeerCount() {
    return network.getPeerCount();
  }

  @Override
  public String getNodeAddress() {
    return network.getNodeAddress();
  }

  @Override
  public NodeId getNodeId() {
    return network.getNodeId();
  }

  @Override
  public Optional<String> getEnr() {
    return network.getEnr();
  }

  @Override
  public Optional<String> getDiscoveryAddress() {
    return network.getDiscoveryAddress();
  }

  @Override
  public int getListenPort() {
    return network.getListenPort();
  }

  @Override
  public SafeFuture<?> start() {
    return network.start();
  }

  @Override
  public SafeFuture<?> stop() {
    return network.stop();
  }

  @Override
  public SafeFuture<?> gossip(final String topic, final Bytes data) {
    return network.gossip(topic, data);
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    return network.subscribe(topic, topicHandler);
  }

  @Override
  public Map<String, Collection<NodeId>> getSubscribersByTopic() {
    return network.getSubscribersByTopic();
  }
}
