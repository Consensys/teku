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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;

public class DelegatingP2PNetwork implements P2PNetwork {
  private final P2PNetwork network;

  public DelegatingP2PNetwork(final P2PNetwork network) {
    this.network = network;
  }

  @Override
  public CompletableFuture<?> connect(final String peer) {
    return network.connect(peer);
  }

  @Override
  public Optional<? extends Peer> getPeer(final NodeId id) {
    return network.getPeer(id);
  }

  @Override
  public Stream<? extends Peer> streamPeers() {
    return network.streamPeers();
  }

  @Override
  public long getPeerCount() {
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
  public CompletableFuture<?> start() {
    return network.start();
  }

  @Override
  public void stop() {
    network.stop();
  }
}
