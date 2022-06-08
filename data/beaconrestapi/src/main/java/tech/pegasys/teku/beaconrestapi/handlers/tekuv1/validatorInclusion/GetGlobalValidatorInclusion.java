/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.validatorInclusion;

import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.JSON;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.MoreObjects;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;

public class GetGlobalValidatorInclusion extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE = "/teku/v1/validator_inclusion/:epoch/global";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private static final Logger LOG = LogManager.getLogger();

  private final ChainDataProvider chainDataProvider;
  private final TimeProvider timeProvider;

  private final Spec spec;

  public GetGlobalValidatorInclusion(
      final DataProvider dataProvider, final Spec spec, final TimeProvider timeProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getGlobalValidatorInclusion")
            .summary("Get Global Validator Inclusion")
            .description("Returns a global count of votes for a given epoch.")
            .tags(TAG_TEKU, TAG_EXPERIMENTAL)
            .pathParam(EPOCH_PARAMETER)
            .response(
                SC_OK,
                "Request successful",
                SerializableTypeDefinition.<GetGlobalValidatorResponseData>object()
                    .name("getGlobalValidatorInclusionResponse")
                    .withField(
                        "data", GetGlobalValidatorResponseData.RESPONSE_DATA, Function.identity())
                    .build())
            .withNotFoundResponse()
            .response(
                SC_SERVICE_UNAVAILABLE,
                "Beacon node is currently syncing.",
                HTTP_ERROR_RESPONSE_TYPE)
            .build());
    this.chainDataProvider = dataProvider.getChainDataProvider();
    this.spec = spec;
    this.timeProvider = timeProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get Global Validator Inclusion",
      tags = {TAG_TEKU, TAG_EXPERIMENTAL},
      description = "Returns a global count of votes for a given epoch.",
      pathParams = {@OpenApiParam(name = "epoch", description = "Epoch to get data for")},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = {@OpenApiContent(type = JSON, from = GetGlobalValidatorResponse.class)}),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(
            status = RES_SERVICE_UNAVAILABLE,
            description = "Beacon node is currently syncing.")
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 epoch = request.getPathParameter(EPOCH_PARAMETER);
    request.respondAsync(
        chainDataProvider
            .getValidatorInclusionStateAtEpoch(epoch)
            .thenApply(
                maybeStateAndMetadata -> {
                  if (maybeStateAndMetadata.isEmpty()) {
                    return AsyncApiResponse.respondWithError(
                        SC_SERVICE_UNAVAILABLE, "Server is currently syncing.");
                  }
                  final BeaconState state = maybeStateAndMetadata.get().getData();
                  return AsyncApiResponse.respondOk(getResponseFromState(state));
                }));
  }

  private GetGlobalValidatorResponseData getResponseFromState(final BeaconState state) {
    final UInt64 timeStart = timeProvider.getTimeInMillis();
    final ValidatorStatuses validatorStatuses =
        spec.atSlot(state.getSlot()).getValidatorStatusFactory().createValidatorStatuses(state);
    final UInt64 timeEnd = timeProvider.getTimeInMillis();
    LOG.debug(
        "Time taken to compute validator statuses: " + timeEnd.minusMinZero(timeStart) + " ms");
    return new GetGlobalValidatorResponseData(validatorStatuses.getTotalBalances());
  }

  public static class GetGlobalValidatorResponse {
    @JsonProperty("data")
    public final GetGlobalValidatorResponseData data;

    public GetGlobalValidatorResponse(final GetGlobalValidatorResponseData data) {
      this.data = data;
    }
  }

  public static class GetGlobalValidatorResponseData {
    public static final SerializableTypeDefinition<GetGlobalValidatorResponseData> RESPONSE_DATA =
        SerializableTypeDefinition.object(GetGlobalValidatorResponseData.class)
            .withField(
                "current_epoch_active_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getCurrentEpochActiveGwei)
            .withField(
                "previous_epoch_active_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getPreviousEpochActiveGwei)
            .withField(
                "current_epoch_target_attesting_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getCurrentEpochTargetAttestingGwei)
            .withField(
                "previous_epoch_target_attesting_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getPreviousEpochTargetAttestingGwei)
            .withField(
                "previous_epoch_head_attesting_gwei",
                UINT64_TYPE,
                GetGlobalValidatorResponseData::getPreviousEpochHeadAttestingGwei)
            .build();
    private final UInt64 currentEpochActiveGwei;
    private final UInt64 previousEpochActiveGwei;
    private final UInt64 currentEpochTargetAttestingGwei;
    private final UInt64 previousEpochTargetAttestingGwei;
    private final UInt64 previousEpochHeadAttestingGwei;

    public GetGlobalValidatorResponseData(final TotalBalances totalBalances) {
      this.currentEpochActiveGwei = totalBalances.getCurrentEpochActiveValidators();
      this.previousEpochActiveGwei = totalBalances.getPreviousEpochActiveValidators();
      this.currentEpochTargetAttestingGwei = totalBalances.getCurrentEpochTargetAttesters();
      this.previousEpochTargetAttestingGwei = totalBalances.getPreviousEpochTargetAttesters();
      this.previousEpochHeadAttestingGwei = totalBalances.getPreviousEpochHeadAttesters();
    }

    @JsonProperty("current_epoch_active_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getCurrentEpochActiveGwei() {
      return currentEpochActiveGwei;
    }

    @JsonProperty("previous_epoch_active_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getPreviousEpochActiveGwei() {
      return previousEpochActiveGwei;
    }

    @JsonProperty("current_epoch_target_attesting_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getCurrentEpochTargetAttestingGwei() {
      return currentEpochTargetAttestingGwei;
    }

    @JsonProperty("previous_epoch_target_attesting_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getPreviousEpochTargetAttestingGwei() {
      return previousEpochTargetAttestingGwei;
    }

    @JsonProperty("previous_epoch_head_attesting_gwei")
    @Schema(type = "string", format = "uint64")
    public UInt64 getPreviousEpochHeadAttestingGwei() {
      return previousEpochHeadAttestingGwei;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final GetGlobalValidatorResponseData that = (GetGlobalValidatorResponseData) o;
      return Objects.equals(currentEpochActiveGwei, that.currentEpochActiveGwei)
          && Objects.equals(previousEpochActiveGwei, that.previousEpochActiveGwei)
          && Objects.equals(currentEpochTargetAttestingGwei, that.currentEpochTargetAttestingGwei)
          && Objects.equals(previousEpochTargetAttestingGwei, that.previousEpochTargetAttestingGwei)
          && Objects.equals(previousEpochHeadAttestingGwei, that.previousEpochHeadAttestingGwei);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          currentEpochActiveGwei,
          previousEpochActiveGwei,
          currentEpochTargetAttestingGwei,
          previousEpochTargetAttestingGwei,
          previousEpochHeadAttestingGwei);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("currentEpochActiveGwei", currentEpochActiveGwei)
          .add("previousEpochActiveGwei", previousEpochActiveGwei)
          .add("currentEpochTargetAttestingGwei", currentEpochTargetAttestingGwei)
          .add("previousEpochTargetAttestingGwei", previousEpochTargetAttestingGwei)
          .add("previousEpochHeadAttestingGwei", previousEpochHeadAttestingGwei)
          .toString();
    }
  }
}
