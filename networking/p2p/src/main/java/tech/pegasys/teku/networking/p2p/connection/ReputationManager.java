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

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.util.cache.Cache;
import tech.pegasys.teku.util.cache.LRUCache;
import tech.pegasys.teku.util.time.TimeProvider;

public class ReputationManager {
  static final UnsignedLong FAILURE_BAN_PERIOD =
      UnsignedLong.valueOf(TimeUnit.MINUTES.toSeconds(2));
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

  private Reputation getOrCreateReputation(final PeerAddress peerAddress) {
    return peerReputations.get(peerAddress.getId(), key -> new Reputation());
  }

  private static class Reputation {
    private volatile Optional<UnsignedLong> lastInitiationFailure = Optional.empty();
    private volatile boolean unsuitable = false;

    public void reportInitiatedConnectionFailed(final UnsignedLong failureTime) {
      lastInitiationFailure = Optional.of(failureTime);
    }

    public boolean shouldInitiateConnection(final UnsignedLong currentTime) {
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
        final UnsignedLong disconnectTime,
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
          && reason.map(r -> r != DisconnectReason.TOO_MANY_PEERS).orElse(false);
    }
  }
}
