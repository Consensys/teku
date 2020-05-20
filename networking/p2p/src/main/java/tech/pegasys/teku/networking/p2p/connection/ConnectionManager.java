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

package tech.pegasys.teku.networking.p2p.connection;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;

public class ConnectionManager extends Service {
  private static final Logger LOG = LogManager.getLogger();
  private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(20);
  private static final Duration DISCOVERY_INTERVAL = Duration.ofSeconds(30);
  private final AsyncRunner asyncRunner;
  private final P2PNetwork<? extends Peer> network;
  private final Set<PeerAddress> staticPeers;
  private final DiscoveryService discoveryService;
  private final TargetPeerRange targetPeerCountRange;
  private final ReputationManager reputationManager;

  private volatile long peerConnectedSubscriptionId;
  private final NewPeerFilter newPeerFilter = new NewPeerFilter();

  public ConnectionManager(
      final DiscoveryService discoveryService,
      final ReputationManager reputationManager,
      final AsyncRunner asyncRunner,
      final P2PNetwork<? extends Peer> network,
      final List<PeerAddress> peerAddresses,
      final TargetPeerRange targetPeerCountRange) {
    this.reputationManager = reputationManager;
    this.asyncRunner = asyncRunner;
    this.network = network;
    this.staticPeers = new HashSet<>(peerAddresses);
    this.discoveryService = discoveryService;
    this.targetPeerCountRange = targetPeerCountRange;
  }

  @Override
  protected SafeFuture<?> doStart() {
    synchronized (this) {
      staticPeers.forEach(this::createPersistentConnection);
    }
    connectToKnownPeers();
    searchForPeers().reportExceptions();
    peerConnectedSubscriptionId = network.subscribeConnect(this::onPeerConnected);
    return SafeFuture.COMPLETE;
  }

  private void connectToKnownPeers() {
    final int maxAttempts = targetPeerCountRange.getPeersToAdd(network.getPeerCount());
    discoveryService
        .streamKnownPeers()
        .filter(newPeerFilter::isPeerValid)
        .map(network::createPeerAddress)
        .filter(reputationManager::isConnectionInitiationAllowed)
        .filter(peerAddress -> !network.isConnected(peerAddress))
        .limit(maxAttempts)
        .forEach(this::attemptConnection);
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

  private void attemptConnection(final PeerAddress discoveryPeer) {
    network
        .connect(discoveryPeer)
        .finish(
            peer -> LOG.trace("Successfully connected to peer {}", peer.getId()),
            error -> LOG.trace(() -> "Failed to connect to peer: " + discoveryPeer.getId(), error));
  }

  private void onPeerConnected(final Peer peer) {
    final int peersToDrop = targetPeerCountRange.getPeersToDrop(network.getPeerCount());
    network
        .streamPeers()
        .filter(candidate -> !staticPeers.contains(candidate.getAddress()))
        .limit(peersToDrop)
        .forEach(peerToDrop -> peerToDrop.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
  }

  @Override
  protected SafeFuture<?> doStop() {
    network.unsubscribeConnect(peerConnectedSubscriptionId);
    return SafeFuture.COMPLETE;
  }

  public synchronized void addStaticPeer(final PeerAddress peerAddress) {
    if (!staticPeers.contains(peerAddress)) {
      staticPeers.add(peerAddress);
      createPersistentConnection(peerAddress);
    }
  }

  private void createPersistentConnection(final PeerAddress peerAddress) {
    maintainPersistentConnection(peerAddress).reportExceptions();
  }

  private SafeFuture<Peer> maintainPersistentConnection(final PeerAddress peerAddress) {
    if (!isRunning()) {
      // We've been stopped so halt the process.
      return new SafeFuture<>();
    }
    LOG.debug("Connecting to peer {}", peerAddress);
    return network
        .connect(peerAddress)
        .thenApply(
            peer -> {
              LOG.debug("Connection to peer {} was successful", peer.getId());
              peer.subscribeDisconnect(
                  () -> {
                    LOG.debug(
                        "Peer {} disconnected. Will try to reconnect in {} sec",
                        peerAddress,
                        RECONNECT_TIMEOUT.toSeconds());
                    asyncRunner
                        .runAfterDelay(
                            () -> maintainPersistentConnection(peerAddress),
                            RECONNECT_TIMEOUT.toMillis(),
                            TimeUnit.MILLISECONDS)
                        .reportExceptions();
                  });
              return peer;
            })
        .exceptionallyCompose(
            error -> {
              LOG.debug(
                  "Connection to {} failed: {}. Will retry in {} sec",
                  peerAddress,
                  error,
                  RECONNECT_TIMEOUT.toSeconds());
              return asyncRunner.runAfterDelay(
                  () -> maintainPersistentConnection(peerAddress),
                  RECONNECT_TIMEOUT.toMillis(),
                  TimeUnit.MILLISECONDS);
            });
  }

  public void addPeerPredicate(final Predicate<DiscoveryPeer> predicate) {
    newPeerFilter.addPeerPredicate(predicate);
  }

  public static class NewPeerFilter {
    private final Set<Predicate<DiscoveryPeer>> peerPredicates = new HashSet<>();

    void addPeerPredicate(final Predicate<DiscoveryPeer> predicate) {
      peerPredicates.add(predicate);
    }

    boolean isPeerValid(DiscoveryPeer peer) {
      return peerPredicates.stream().allMatch(predicate -> predicate.test(peer));
    }
  }
}
