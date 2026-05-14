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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;

/**
 * Maps Teku-internal reason identifiers (from {@link ReputationAdjustment} and {@link
 * DisconnectReason}) onto the controlled vocabularies proposed for the simplified beacon-API peer
 * scoring spec.
 *
 * <p>Teku's reputation adjustments are coarse (just LARGE/SMALL penalty/reward) and don't carry
 * structured downscore reasons, so penalty adjustments map to {@code "unknown"}. Disconnect reasons
 * map to the {@code PeerDisconnectReason} enum where there is a direct equivalent.
 */
final class PeerScoreReasonMapper {

  private static final Set<String> REPUTATION_ADJUSTMENT_NAMES =
      Set.of(
          ReputationAdjustment.LARGE_PENALTY.name(),
          ReputationAdjustment.SMALL_PENALTY.name(),
          ReputationAdjustment.LARGE_REWARD.name(),
          ReputationAdjustment.SMALL_REWARD.name());

  private PeerScoreReasonMapper() {}

  /**
   * Map a raw Teku reason string (either a {@link ReputationAdjustment} name or a {@link
   * DisconnectReason} name) onto the spec's {@code PeerScoreReason} vocabulary. Returns empty if
   * the value is not a recognised reputation adjustment (e.g. it's a disconnect reason, which has
   * its own mapping).
   */
  static Optional<String> mapDownscoreReason(final String rawReason) {
    if (rawReason == null) {
      return Optional.empty();
    }
    if (!REPUTATION_ADJUSTMENT_NAMES.contains(rawReason)) {
      return Optional.empty();
    }
    // Teku's reputation adjustments don't carry structured downscore context, so we surface
    // "unknown" rather than guessing at a more specific code.
    return Optional.of("unknown");
  }

  /**
   * Map a raw Teku reason string onto the spec's {@code PeerDisconnectReason} vocabulary if it
   * corresponds to a {@link DisconnectReason}, otherwise empty.
   */
  static Optional<String> mapDisconnectReason(final String rawReason) {
    if (rawReason == null) {
      return Optional.empty();
    }
    final DisconnectReason reason;
    try {
      reason = DisconnectReason.valueOf(rawReason);
    } catch (final IllegalArgumentException e) {
      return Optional.empty();
    }
    return Optional.of(mapDisconnectReason(reason));
  }

  private static String mapDisconnectReason(final DisconnectReason reason) {
    return switch (reason) {
      case BAD_SCORE -> "bad_score";
      case IRRELEVANT_NETWORK, UNABLE_TO_VERIFY_NETWORK -> "irrelevant_network";
      case TOO_MANY_PEERS -> "too_many_peers";
      case RATE_LIMITING -> "rate_limited";
      case SHUTTING_DOWN -> "client_shutdown";
      case REMOTE_FAULT, UNRESPONSIVE, NO_STATUS_RECEIVED, DUPLICATE_CONNECTION -> "io_error";
      case BANNED -> "bad_score";
    };
  }
}
