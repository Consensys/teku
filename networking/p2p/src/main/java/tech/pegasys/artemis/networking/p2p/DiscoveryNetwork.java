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

package tech.pegasys.artemis.networking.p2p;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.connection.ConnectionManager;
import tech.pegasys.artemis.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.artemis.networking.p2p.discovery.discv5.DiscV5Service;
import tech.pegasys.artemis.networking.p2p.discovery.noop.NoOpDiscoveryService;
import tech.pegasys.artemis.networking.p2p.network.DelegatingP2PNetwork;
import tech.pegasys.artemis.networking.p2p.network.NetworkConfig;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.artemis.util.async.DelayedExecutorAsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;

public class DiscoveryNetwork<P extends Peer> extends DelegatingP2PNetwork<P> {
  private static final Logger LOG = LogManager.getLogger();
  private final P2PNetwork<P> p2pNetwork;
  private final DiscoveryService discoveryService;
  private final ConnectionManager connectionManager;

  DiscoveryNetwork(
      final P2PNetwork<P> p2pNetwork,
      final DiscoveryService discoveryService,
      final ConnectionManager connectionManager) {
    super(p2pNetwork);
    this.p2pNetwork = p2pNetwork;
    this.discoveryService = discoveryService;
    this.connectionManager = connectionManager;
  }

  public static <P extends Peer> DiscoveryNetwork<P> create(
      final P2PNetwork<P> p2pNetwork, final NetworkConfig p2pConfig) {
    final DiscoveryService discoveryService = createDiscoveryService(p2pConfig);
    final ConnectionManager connectionManager =
        new ConnectionManager(
            discoveryService,
            DelayedExecutorAsyncRunner.create(),
            p2pNetwork,
            p2pConfig.getStaticPeers(),
            // TODO: Make this configurable
            new TargetPeerRange(10, 20));
    return new DiscoveryNetwork<>(p2pNetwork, discoveryService, connectionManager);
  }

  private static DiscoveryService createDiscoveryService(final NetworkConfig p2pConfig) {
    final DiscoveryService discoveryService;
    switch (p2pConfig.getDiscoveryMethod()) {
      case "discv5":
        discoveryService =
            DiscV5Service.create(
                Bytes.wrap(p2pConfig.getPrivateKey().raw()),
                p2pConfig.getNetworkInterface(),
                p2pConfig.getListenPort(),
                p2pConfig.getBootnodes());
        break;
      case "static":
        discoveryService = new NoOpDiscoveryService();
        break;
      default:
        throw new IllegalArgumentException(
            "Unknown discovery method: " + p2pConfig.getDiscoveryMethod());
    }
    return discoveryService;
  }

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.allOf(p2pNetwork.start(), discoveryService.start())
        .thenCompose(__ -> connectionManager.start());
  }

  @Override
  public void stop() {
    connectionManager
        .stop()
        .exceptionally(
            error -> {
              LOG.error("Failed to stop connection manager", error);
              return null;
            })
        .always(
            () -> {
              p2pNetwork.stop();
              discoveryService.stop().reportExceptions();
            });
  }

  public void addStaticPeer(final String peer) {
    connectionManager.addStaticPeer(peer);
  }

  @Override
  public Optional<String> getEnr() {
    return discoveryService.getEnr();
  }

  @Override
  public long subscribeConnect(final PeerConnectedSubscriber<P> subscriber) {
    return p2pNetwork.subscribeConnect(subscriber);
  }

  @Override
  public void unsubscribeConnect(final long subscriptionId) {
    p2pNetwork.unsubscribeConnect(subscriptionId);
  }

  @Override
  public Optional<P> getPeer(final NodeId id) {
    return p2pNetwork.getPeer(id);
  }

  @Override
  public Stream<P> streamPeers() {
    return p2pNetwork.streamPeers();
  }
}
