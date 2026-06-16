/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.Optional;
import java.util.OptionalInt;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public interface ReputationManager {
  ReputationManager NOOP =
      new ReputationManager() {
        @Override
        public void reportInitiatedConnectionFailed(PeerAddress peerAddress) {}

        @Override
        public boolean isConnectionInitiationAllowed(PeerAddress peerAddress) {
          return true;
        }

        @Override
        public void reportInitiatedConnectionSuccessful(PeerAddress peerAddress) {}

        @Override
        public void reportDisconnection(
            PeerAddress peerAddress, Optional<DisconnectReason> reason, boolean locallyInitiated) {}

        @Override
        public boolean adjustReputation(PeerAddress peerAddress, ReputationAdjustment effect) {
          return false;
        }
      };

  void reportInitiatedConnectionFailed(final PeerAddress peerAddress);

  boolean isConnectionInitiationAllowed(final PeerAddress peerAddress);

  void reportInitiatedConnectionSuccessful(final PeerAddress peerAddress);

  void reportDisconnection(
      final PeerAddress peerAddress,
      final Optional<DisconnectReason> reason,
      final boolean locallyInitiated);

  /**
   * @return boolean representing whether the manager should disconnect.
   */
  boolean adjustReputation(final PeerAddress peerAddress, final ReputationAdjustment effect);

  /**
   * Return the current internal reputation score for the given peer, if one is tracked.
   *
   * @param nodeId the id of the peer.
   * @return the integer reputation score, or {@link OptionalInt#empty()} if the peer is not
   *     tracked.
   */
  default OptionalInt getReputationScore(final NodeId nodeId) {
    return OptionalInt.empty();
  }

  /**
   * Return the most recent reputation adjustment (or disconnect event) recorded for the given
   * peer, if any.
   */
  default Optional<LastAdjustment> getLastAdjustment(final NodeId nodeId) {
    return Optional.empty();
  }

  /**
   * Snapshot of the most recent event that influenced a peer's reputation. {@code reason} is
   * either a {@link ReputationAdjustment} or {@link DisconnectReason} enum name. {@code delta} is
   * the change applied to the internal reputation score (0 for disconnects that don't move the
   * score). {@code atMs} is the wall-clock time the event occurred, in milliseconds since epoch.
   */
  record LastAdjustment(String reason, double delta, long atMs) {}
}
