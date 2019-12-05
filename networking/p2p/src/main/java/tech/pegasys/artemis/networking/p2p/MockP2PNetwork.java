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

package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
import io.libp2p.core.PeerId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.LibP2PNodeId;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;

public class MockP2PNetwork implements P2PNetwork {
  private final NodeId nodeId = new LibP2PNodeId(PeerId.random());

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
  public Optional<? extends Peer> getPeer(final NodeId id) {
    return Optional.empty();
  }

  @Override
  public Stream<? extends Peer> streamPeers() {
    return Stream.empty();
  }

  @Override
  public String getNodeAddress() {
    return "/ip4/127.0.0.1" + "/p2p/" + nodeId.toBase58();
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
}
