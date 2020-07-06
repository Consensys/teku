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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.Cancellable;
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
  private final Counter attemptedConnectionCounter;
  private final Counter successfulConnectionCounter;
  private final Counter failedConnectionCounter;
  private final Collection<Predicate<DiscoveryPeer>> peerPredicates = new CopyOnWriteArrayList<>();

  private volatile long peerConnectedSubscriptionId;
  private volatile Cancellable periodicPeerSearch;

  public ConnectionManager(
      final MetricsSystem metricsSystem,
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

    final LabelledMetric<Counter> connectionAttemptCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "peer_connection_attempt_count",
            "Total number of outbound connection attempts made",
            "status");
    attemptedConnectionCounter = connectionAttemptCounter.labels("attempted");
    successfulConnectionCounter = connectionAttemptCounter.labels("successful");
    failedConnectionCounter = connectionAttemptCounter.labels("failed");
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.trace("Starting discovery manager");
    synchronized (this) {
      staticPeers.forEach(this::createPersistentConnection);
    }
    periodicPeerSearch =
        asyncRunner.runWithFixedDelay(
            this::searchForPeers,
            DISCOVERY_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS,
            error -> LOG.error("Error while searching for peers", error));
    connectToKnownPeers();
    searchForPeers();
    peerConnectedSubscriptionId = network.subscribeConnect(this::onPeerConnected);
    return SafeFuture.COMPLETE;
  }

  private void connectToKnownPeers() {
    final int maxAttempts = targetPeerCountRange.getPeersToAdd(network.getPeerCount());
    LOG.trace("Connecting to up to {} known peers", maxAttempts);
    discoveryService
        .streamKnownPeers()
        .filter(this::isPeerValid)
        .map(network::createPeerAddress)
        .filter(reputationManager::isConnectionInitiationAllowed)
        .filter(peerAddress -> !network.isConnected(peerAddress))
        .limit(maxAttempts)
        .forEach(this::attemptConnection);
  }

  private void searchForPeers() {
    if (!isRunning()) {
      LOG.trace("Not running so not searching for peers");
      return;
    }
    LOG.trace("Searching for peers");
    discoveryService
        .searchForPeers()
        .orTimeout(10, TimeUnit.SECONDS)
        .finish(
            this::connectToKnownPeers,
            error -> {
              LOG.debug("Discovery failed", error);
              connectToKnownPeers();
            });
  }

  private void attemptConnection(final PeerAddress discoveryPeer) {
    LOG.trace("Attempting to connect to {}", discoveryPeer.getId());
    attemptedConnectionCounter.inc();
    network
        .connect(discoveryPeer)
        .finish(
            peer -> {
              LOG.trace("Successfully connected to peer {}", peer.getId());
              successfulConnectionCounter.inc();
            },
            error -> {
              LOG.trace(() -> "Failed to connect to peer: " + discoveryPeer.getId(), error);
              failedConnectionCounter.inc();
            });
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
    final Cancellable peerSearchTask = this.periodicPeerSearch;
    if (peerSearchTask != null) {
      peerSearchTask.cancel();
    }
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
    attemptedConnectionCounter.inc();
    return network
        .connect(peerAddress)
        .thenApply(
            peer -> {
              LOG.debug("Connection to peer {} was successful", peer.getId());
              successfulConnectionCounter.inc();
              peer.subscribeDisconnect(
                  (reason, locallyInitiated) -> {
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
              failedConnectionCounter.inc();
              return asyncRunner.runAfterDelay(
                  () -> maintainPersistentConnection(peerAddress),
                  RECONNECT_TIMEOUT.toMillis(),
                  TimeUnit.MILLISECONDS);
            });
  }

  public void addPeerPredicate(final Predicate<DiscoveryPeer> predicate) {
    peerPredicates.add(predicate);
  }

  private boolean isPeerValid(DiscoveryPeer peer) {
    return !peer.getNodeAddress().getAddress().isAnyLocalAddress()
        && peerPredicates.stream().allMatch(predicate -> predicate.test(peer));
  }
}
