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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.node;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_DOUBLE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.RAW_INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;

public class GetPeersScore extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/nodes/peer_scores";
  private final NetworkDataProvider network;

  private static final SerializableTypeDefinition<LastAction> LAST_ACTION_TYPE =
      SerializableTypeDefinition.object(LastAction.class)
          .withField("reason", STRING_TYPE, LastAction::reason)
          .withField("delta", RAW_DOUBLE_TYPE, LastAction::delta)
          .withField("seconds_ago", RAW_INTEGER_TYPE, LastAction::secondsAgo)
          .build();

  private static final SerializableTypeDefinition<PeerScore> PEER_TYPE =
      SerializableTypeDefinition.object(PeerScore.class)
          .withField("peer_id", STRING_TYPE, PeerScore::peerId)
          .withField("gossip_score", RAW_DOUBLE_TYPE, PeerScore::gossipScore)
          .withOptionalField("reputation_score", RAW_INTEGER_TYPE, PeerScore::reputationScore)
          .withOptionalField("last_action", LAST_ACTION_TYPE, PeerScore::lastAction)
          .build();

  private static final SerializableTypeDefinition<List<PeerScore>> RESPONSE_TYPE =
      SerializableTypeDefinition.<List<PeerScore>>object()
          .name("GetPeerScoresResponse")
          .withField("data", listOf(PEER_TYPE), Function.identity())
          .build();

  public GetPeersScore(final DataProvider provider) {
    this(provider.getNetworkDataProvider());
  }

  GetPeersScore(final NetworkDataProvider network) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPeersScore")
            .summary("Get peer scores")
            .description("Retrieves data about the node's network peers.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.network = network;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    final ReputationManager reputationManager = network.getReputationManager();
    final long nowMs = System.currentTimeMillis();
    final List<PeerScore> data =
        network.getPeerScores().stream().map(peer -> toPeerScore(peer, reputationManager, nowMs)).toList();
    request.respondOk(data);
  }

  private static PeerScore toPeerScore(
      final Eth2Peer peer, final ReputationManager reputationManager, final long nowMs) {
    final Optional<Integer> reputationScore =
        toBoxed(reputationManager.getReputationScore(peer.getId()));
    final Optional<LastAction> lastAction =
        reputationManager
            .getLastAdjustment(peer.getId())
            .map(
                adjustment ->
                    new LastAction(
                        adjustment.reason(),
                        adjustment.delta(),
                        secondsSince(nowMs, adjustment.atMs())));
    return new PeerScore(
        peer.getId().toBase58(), peer.getGossipScore(), reputationScore, lastAction);
  }

  private static Optional<Integer> toBoxed(final OptionalInt value) {
    return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
  }

  private static int secondsSince(final long nowMs, final long thenMs) {
    final long ageSeconds = Math.max(0L, (nowMs - thenMs) / 1000L);
    return (int) Math.min(ageSeconds, Integer.MAX_VALUE);
  }

  record PeerScore(
      String peerId,
      Double gossipScore,
      Optional<Integer> reputationScore,
      Optional<LastAction> lastAction) {}

  record LastAction(String reason, double delta, int secondsAgo) {}
}
