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

package tech.pegasys.artemis.networking.p2p.connection;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.util.cache.Cache;
import tech.pegasys.artemis.datastructures.util.cache.LRUCache;
import tech.pegasys.artemis.networking.p2p.network.PeerAddress;
import tech.pegasys.artemis.util.time.TimeProvider;

public class ReputationManager {
  private static final Logger LOG = LogManager.getLogger();
  private final TimeProvider timeProvider;
  private final Cache<PeerAddress, Reputation> peerReputations;

  public ReputationManager(final TimeProvider timeProvider, final int capacity) {
    this.timeProvider = timeProvider;
    this.peerReputations = new LRUCache<>(capacity);
  }

  public void reportInitiatedConnectionFailed(final PeerAddress peerAddress) {
    LOG.trace("Connection to {} failed", peerAddress);
    getOrCreateReputation(peerAddress)
        .reportInitiatedConnectionFailed(timeProvider.getTimeInSeconds());
  }

  public boolean isConnectionInitiationAllowed(final PeerAddress peerAddress) {
    final boolean result =
        peerReputations
            .getCached(peerAddress)
            .map(reputation -> reputation.shouldInitiateConnection(timeProvider.getTimeInSeconds()))
            .orElse(true);
    LOG.trace("Connection to {} allowed: {}", peerAddress, result);
    return result;
  }

  public void reportInitiatedConnectionSuccessful(final PeerAddress peerAddress) {
    LOG.trace("Connection to {} successful", peerAddress);
    getOrCreateReputation(peerAddress).reportInitiatedConnectionSuccessful();
  }

  private Reputation getOrCreateReputation(final PeerAddress peerAddress) {
    return peerReputations.get(peerAddress, key -> new Reputation());
  }

  private static class Reputation {
    private static final UnsignedLong FAILURE_BAN_PERIOD = UnsignedLong.valueOf(60); // Seconds
    private volatile Optional<UnsignedLong> lastInitiationFailure = Optional.empty();

    public void reportInitiatedConnectionFailed(final UnsignedLong failureTime) {
      lastInitiationFailure = Optional.of(failureTime);
    }

    public boolean shouldInitiateConnection(final UnsignedLong currentTime) {
      return lastInitiationFailure
          .map(
              lastFailureTime ->
                  lastFailureTime.plus(FAILURE_BAN_PERIOD).compareTo(currentTime) < 0)
          .orElse(true);
    }

    public void reportInitiatedConnectionSuccessful() {
      lastInitiationFailure = Optional.empty();
    }
  }
}
