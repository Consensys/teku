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

package tech.pegasys.artemis.networking.p2p.mock;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import tech.pegasys.artemis.networking.p2p.gossip.TopicChannel;
import tech.pegasys.artemis.networking.p2p.gossip.TopicHandler;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.networking.p2p.peer.PeerConnectedSubscriber;

public class MockP2PNetwork implements P2PNetwork<Peer> {
  private final int port = 6000;
  private final NodeId nodeId = new MockNodeId();

  public MockP2PNetwork(EventBus eventBus) {
    eventBus.register(this);
  }

  @Override
  public CompletableFuture<?> connect(String peer) {
    final CompletableFuture<?> connectFuture = new CompletableFuture<>();
    connectFuture.completeExceptionally(new UnsupportedOperationException());
    return connectFuture;
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<Peer> subscriber) {
    return 0;
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    // Nothing to do
  }

  @Override
  public Optional<Peer> getPeer(final NodeId id) {
    return Optional.empty();
  }

  @Override
  public Stream<Peer> streamPeers() {
    return Stream.empty();
  }

  @Override
  public long getPeerCount() {
    return 0L;
  }

  @Override
  public String getNodeAddress() {
    return "/ip4/127.0.0.1/tcp/" + port + "/p2p/" + nodeId.toBase58();
  }

  @Override
  public NodeId getNodeId() {
    return nodeId;
  }

  /** Stops the P2P network layer. */
  @Override
  public void stop() {}

  @Override
  public CompletableFuture<?> start() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public TopicChannel subscribe(final String topic, final TopicHandler topicHandler) {
    return new MockTopicChannel();
  }
}
