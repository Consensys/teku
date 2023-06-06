/*
 * Copyright ConsenSys Software Inc., 2023
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
import java.util.concurrent.TimeUnit;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;

public interface ReputationManager {
  // This is not a big ban, we expect the peer could be usable very soon
  UInt64 COOLDOWN_PERIOD = UInt64.valueOf(TimeUnit.MINUTES.toSeconds(2));
  // It's a big ban, we expect that the peer is not useful until major changes, for example,
  // software update
  UInt64 BAN_PERIOD = UInt64.valueOf(TimeUnit.HOURS.toSeconds(12));

  int LARGE_CHANGE = 10;
  int SMALL_CHANGE = 3;

  void reportInitiatedConnectionFailed(final PeerAddress peerAddress);

  boolean isConnectionInitiationAllowed(final PeerAddress peerAddress);

  void reportInitiatedConnectionSuccessful(final PeerAddress peerAddress);

  void reportDisconnection(
      final PeerAddress peerAddress,
      final Optional<DisconnectReason> reason,
      final boolean locallyInitiated);

  boolean adjustReputation(final PeerAddress peerAddress, final ReputationAdjustment effect);
}
