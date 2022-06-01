/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.teku.GetEth1VotesStatsResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider.VotingPeriodInfo;

public class GetEth1VotesStats extends MigratingEndpointAdapter {

  public static final String OAPI_ROUTE = "/teku/v1/beacon/state/:state_id/eth1stats";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  public static final String NOT_FOUND_MESSAGE = "Corresponding state not found";

  private static final SerializableTypeDefinition<Pair<Eth1Data, UInt64>> ETH1DATA_WITH_VOTES_TYPE =
      SerializableTypeDefinition.<Pair<Eth1Data, UInt64>>object()
          .name("Eth1DataWithVotes")
          .withField("eth1_data", Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition(), Pair::getLeft)
          .withField("votes", CoreTypes.UINT64_TYPE, Pair::getRight)
          .build();

  private static final SerializableTypeDefinition<Eth1DataVotesStats> ETH1VOTES_STATS_TYPE =
      SerializableTypeDefinition.<Eth1DataVotesStats>object()
          .name("Eth1VotesStats")
          .withField(
              "eth1_data",
              Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition(),
              Eth1DataVotesStats::getEth1Data)
          .withField(
              "eth1_data_votes",
              listOf(ETH1DATA_WITH_VOTES_TYPE),
              Eth1DataVotesStats::getVotesBreakdown)
          .withField(
              "win_votes_required", CoreTypes.UINT64_TYPE, Eth1DataVotesStats::getWinVotesRequired)
          .withField(
              "voting_period_slots",
              CoreTypes.UINT64_TYPE,
              Eth1DataVotesStats::getVotingPeriodSlots)
          .withField(
              "voting_period_slots_left",
              CoreTypes.UINT64_TYPE,
              Eth1DataVotesStats::getVotingPeriodSlotsLeft)
          .build();

  private static final SerializableTypeDefinition<Eth1DataVotesStats>
      ETH1VOTES_STATS_RESPONSE_TYPE =
          SerializableTypeDefinition.<Eth1DataVotesStats>object()
              .name("GetEth1VotesStatsResponse")
              .withField("data", ETH1VOTES_STATS_TYPE, Function.identity())
              .build();

  private final ChainDataProvider chainDataProvider;
  private final Eth1DataProvider eth1DataProvider;

  public GetEth1VotesStats(
      final DataProvider dataProvider, final Eth1DataProvider eth1DataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getEth1VotesStats")
            .summary("Get Eth1DataVotes stats")
            .description(
                "Returns information about the current state of voting for Eth1Data from the specified state.")
            .pathParam(PARAMETER_STATE_ID)
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", ETH1VOTES_STATS_RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = dataProvider.getChainDataProvider();
    this.eth1DataProvider = eth1DataProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get Eth1DataVotes stats",
      description =
          "Returns information about the current state of voting for Eth1Data from the specified state.",
      pathParams = {@OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION)},
      tags = {TAG_TEKU},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetEth1VotesStatsResponse.class)),
        @OpenApiResponse(status = RES_NOT_FOUND, description = NOT_FOUND_MESSAGE),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        chainDataProvider
            .getBeaconStateAndMetadata(request.getPathParameter(PARAMETER_STATE_ID))
            .thenApply(
                maybeStateAndMetadata -> {
                  if (maybeStateAndMetadata.isEmpty()) {
                    return AsyncApiResponse.respondWithError(SC_NOT_FOUND, NOT_FOUND_MESSAGE);
                  } else {
                    List<Pair<Eth1Data, UInt64>> votesBreakDown =
                        eth1DataProvider.getEth1DataVotesBreakdown(maybeStateAndMetadata.get());
                    VotingPeriodInfo votingPeriodInfo =
                        eth1DataProvider.getVotingPeriodInfo(maybeStateAndMetadata.get());
                    return AsyncApiResponse.respondOk(
                        new Eth1DataVotesStats(
                            maybeStateAndMetadata.get().getData().getEth1Data(),
                            votesBreakDown,
                            votingPeriodInfo));
                  }
                }));
  }

  static class Eth1DataVotesStats {
    private final Eth1Data eth1Data;
    private final List<Pair<Eth1Data, UInt64>> votesBreakDown;
    private final VotingPeriodInfo votingPeriodInfo;

    public Eth1DataVotesStats(
        final Eth1Data eth1Data,
        final List<Pair<Eth1Data, UInt64>> votesBreakDown,
        final VotingPeriodInfo votingPeriodInfo) {
      this.eth1Data = eth1Data;
      this.votesBreakDown = votesBreakDown;
      this.votingPeriodInfo = votingPeriodInfo;
    }

    public Eth1Data getEth1Data() {
      return eth1Data;
    }

    public List<Pair<Eth1Data, UInt64>> getVotesBreakdown() {
      return votesBreakDown;
    }

    public UInt64 getVotingPeriodSlots() {
      return votingPeriodInfo.getVotingSlots();
    }

    public UInt64 getVotingPeriodSlotsLeft() {
      return votingPeriodInfo.getVotingSlotsLeft();
    }

    public UInt64 getWinVotesRequired() {
      return votingPeriodInfo.getWinVotesRequired();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Eth1DataVotesStats that = (Eth1DataVotesStats) o;
      return Objects.equals(eth1Data, that.eth1Data)
          && Objects.equals(votesBreakDown, that.votesBreakDown)
          && Objects.equals(votingPeriodInfo, that.votingPeriodInfo);
    }

    @Override
    public int hashCode() {
      return Objects.hash(eth1Data, votesBreakDown, votingPeriodInfo);
    }
  }
}
