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

package tech.pegasys.artemis.networking.p2p.discovery;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.networking.p2p.network.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;

public class ConnectionManager extends Service {
  private static final Logger LOG = LogManager.getLogger();
  private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(20);
  private static final Duration DISCOVERY_INTERVAL = Duration.ofSeconds(30);
  private final AsyncRunner asyncRunner;
  private final P2PNetwork<? extends Peer> network;
  private final List<String> staticPeers;
  private final DiscoveryService discoveryService;

  public ConnectionManager(
      final DiscoveryService discoveryService,
      final AsyncRunner asyncRunner,
      final P2PNetwork<? extends Peer> network,
      final List<String> staticPeers) {
    this.asyncRunner = asyncRunner;
    this.network = network;
    this.staticPeers = staticPeers;
    this.discoveryService = discoveryService;
  }

  @Override
  protected SafeFuture<?> doStart() {
    staticPeers.forEach(this::createPersistentConnection);
    connectToKnownPeers();
    searchForPeers().reportExceptions();
    return SafeFuture.COMPLETE;
  }

  private void connectToKnownPeers() {
    discoveryService.streamKnownPeers().forEach(this::attemptConnection);
  }

  private SafeFuture<Void> searchForPeers() {
    if (!isRunning()) {
      return SafeFuture.COMPLETE;
    }
    return SafeFuture.of(discoveryService.searchForPeers())
        .orTimeout(10, TimeUnit.SECONDS)
        .exceptionally(
            error -> {
              LOG.debug("Discovery failed", error);
              return null;
            })
        .thenCompose(
            __ -> {
              connectToKnownPeers();
              return asyncRunner.runAfterDelay(
                  this::searchForPeers, DISCOVERY_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
            });
  }

  private void attemptConnection(final DiscoveryPeer discoveryPeer) {
    if (network.getPeer(discoveryPeer.getNodeId()).isPresent()) {
      LOG.trace("Not connecting to {} as we are already connected", discoveryPeer::getNodeId);
      return;
    }
    LOG.trace(
        "Attempting connection to {} at {}",
        discoveryPeer::getNodeId,
        discoveryPeer::getNodeAddress);
    network
        .connect(discoveryPeer)
        .finish(
            peer -> LOG.trace("Successfully connected to peer {}", peer.getId()),
            error ->
                LOG.trace(() -> "Failed to connect to peer: " + discoveryPeer.getNodeId(), error));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  private void createPersistentConnection(final String peerAddress) {
    maintainPersistentConnection(peerAddress).reportExceptions();
  }

  private SafeFuture<Peer> maintainPersistentConnection(final String peerAddress) {
    LOG.debug("Connecting to peer {}", peerAddress);
    return network
        .connect(peerAddress)
        .exceptionallyCompose(
            error -> {
              if (!isRunning()) {
                // We've been stopped so halt the process.
                return new SafeFuture<>();
              }
              LOG.debug(
                  "Connection to {} failed: {}. Will retry in {} sec",
                  peerAddress,
                  error,
                  RECONNECT_TIMEOUT.toSeconds());
              return asyncRunner.runAfterDelay(
                  () -> maintainPersistentConnection(peerAddress),
                  RECONNECT_TIMEOUT.toMillis(),
                  TimeUnit.MILLISECONDS);
            })
        .thenApply(
            peer -> {
              LOG.debug("Connection to peer {} was successful", peer.getId());
              peer.subscribeDisconnect(() -> createPersistentConnection(peerAddress));
              return peer;
            });
  }
}
