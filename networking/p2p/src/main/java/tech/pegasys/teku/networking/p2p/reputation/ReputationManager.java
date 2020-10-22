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

package tech.pegasys.teku.networking.p2p.reputation;

import static tech.pegasys.teku.networking.p2p.peer.DisconnectReason.TOO_MANY_PEERS;
import static tech.pegasys.teku.networking.p2p.peer.DisconnectReason.UNRESPONSIVE;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class ReputationManager {
  static final UInt64 FAILURE_BAN_PERIOD = UInt64.valueOf(TimeUnit.MINUTES.toSeconds(2));

  static final int LARGE_CHANGE = 10;
  static final int SMALL_CHANGE = 3;
  private static final int DISCONNECT_THRESHOLD = -LARGE_CHANGE;
  private static final int MAX_REPUTATION_SCORE = 2 * LARGE_CHANGE;

  private final TimeProvider timeProvider;
  private final Cache<NodeId, Reputation> peerReputations;

  public ReputationManager(
      final MetricsSystem metricsSystem, final TimeProvider timeProvider, final int capacity) {
    this.timeProvider = timeProvider;
    this.peerReputations = new LRUCache<>(capacity);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.NETWORK,
        "peer_reputation_cache_size",
        "Total number of peer reputations tracked",
        peerReputations::size);
  }

  public void reportInitiatedConnectionFailed(final PeerAddress peerAddress) {
    getOrCreateReputation(peerAddress)
        .reportInitiatedConnectionFailed(timeProvider.getTimeInSeconds());
  }

  public boolean isConnectionInitiationAllowed(final PeerAddress peerAddress) {
    return peerReputations
        .getCached(peerAddress.getId())
        .map(reputation -> reputation.shouldInitiateConnection(timeProvider.getTimeInSeconds()))
        .orElse(true);
  }

  public void reportInitiatedConnectionSuccessful(final PeerAddress peerAddress) {
    getOrCreateReputation(peerAddress).reportInitiatedConnectionSuccessful();
  }

  public void reportDisconnection(
      final PeerAddress peerAddress,
      final Optional<DisconnectReason> reason,
      final boolean locallyInitiated) {
    getOrCreateReputation(peerAddress)
        .reportDisconnection(timeProvider.getTimeInSeconds(), reason, locallyInitiated);
  }

  /**
   * Adjust the reputation score for a peer either positively or negatively.
   *
   * @param peerAddress the address of the peer to adjust the score for.
   * @param effect the reputation change to apply.
   * @return true if the peer should be disconnected, otherwise false.
   */
  public boolean adjustReputation(
      final PeerAddress peerAddress, final ReputationAdjustment effect) {
    return getOrCreateReputation(peerAddress).adjustReputation(effect);
  }

  private Reputation getOrCreateReputation(final PeerAddress peerAddress) {
    return peerReputations.get(peerAddress.getId(), key -> new Reputation());
  }

  private static class Reputation {
    private static final EnumSet<DisconnectReason> LOCAL_TEMPORARY_DISCONNECT_REASONS =
        EnumSet.of(
            // We're currently at limit so don't mark peer unsuitable
            TOO_MANY_PEERS,
            // Peer may have been unresponsive due to a temporary network issue. In particular
            // our internet access may have failed and all peers could be unresponsive.
            // If we consider them all permanently unsuitable we may not be able to rejoin the
            // network once our internet access is restored.
            UNRESPONSIVE);

    private volatile Optional<UInt64> lastInitiationFailure = Optional.empty();
    private volatile boolean unsuitable = false;
    private final AtomicInteger score = new AtomicInteger(0);

    public void reportInitiatedConnectionFailed(final UInt64 failureTime) {
      lastInitiationFailure = Optional.of(failureTime);
    }

    public boolean shouldInitiateConnection(final UInt64 currentTime) {
      return !unsuitable
          && lastInitiationFailure
              .map(
                  lastFailureTime ->
                      lastFailureTime.plus(FAILURE_BAN_PERIOD).compareTo(currentTime) < 0)
              .orElse(true);
    }

    public void reportInitiatedConnectionSuccessful() {
      lastInitiationFailure = Optional.empty();
    }

    public void reportDisconnection(
        final UInt64 disconnectTime,
        final Optional<DisconnectReason> reason,
        final boolean locallyInitiated) {
      if (isLocallyConsideredUnsuitable(reason, locallyInitiated)
          || reason.map(DisconnectReason::isPermanent).orElse(false)) {
        unsuitable = true;
      } else {
        lastInitiationFailure = Optional.of(disconnectTime);
      }
    }

    private boolean isLocallyConsideredUnsuitable(
        final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
      return locallyInitiated
          && reason.map(r -> !LOCAL_TEMPORARY_DISCONNECT_REASONS.contains(r)).orElse(false);
    }

    public boolean adjustReputation(final ReputationAdjustment effect) {
      final int newScore =
          score.updateAndGet(
              current -> Math.min(MAX_REPUTATION_SCORE, current + effect.getScoreChange()));
      final boolean shouldDisconnect = newScore <= DISCONNECT_THRESHOLD;
      if (shouldDisconnect) {
        // Prevent our node from connecting out to the remote peer.
        unsuitable = true;
      }
      return shouldDisconnect;
    }
  }
}
