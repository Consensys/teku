/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetProposerDutiesResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;

public class GetProposerDuties extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE = "/eth/v1/validator/duties/proposer/:epoch";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);

  private static final SerializableTypeDefinition<ProposerDuty> PROPOSER_DUTY_TYPE =
      SerializableTypeDefinition.object(ProposerDuty.class)
          .withField("pubkey", PUBLIC_KEY_TYPE, ProposerDuty::getPublicKey)
          .withField("validator_index", INTEGER_TYPE, ProposerDuty::getValidatorIndex)
          .withField("slot", UINT64_TYPE, ProposerDuty::getSlot)
          .build();

  private static final SerializableTypeDefinition<ProposerDuties> RESPONSE_TYPE =
      SerializableTypeDefinition.object(ProposerDuties.class)
          .name("GetProposerDutiesResponse")
          .withOptionalField(
              EXECUTION_OPTIMISTIC,
              BOOLEAN_TYPE,
              (proposerDuties) ->
                  proposerDuties.isExecutionOptimistic() ? Optional.of(true) : Optional.empty())
          .withField("dependent_root", BYTES32_TYPE, ProposerDuties::getDependentRoot)
          .withField("data", listOf(PROPOSER_DUTY_TYPE), ProposerDuties::getDuties)
          .build();

  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;

  public GetProposerDuties(final DataProvider dataProvider) {
    this(dataProvider.getSyncDataProvider(), dataProvider.getValidatorDataProvider());
  }

  GetProposerDuties(
      final SyncDataProvider syncDataProvider, final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getProposerDuties")
            .summary("Get proposer duties")
            .description(
                "Request beacon node to provide all validators that are scheduled to propose a block in the given epoch.\n\n"
                    + "Duties should only need to be checked once per epoch, "
                    + "however a chain reorganization could occur that results in a change of duties. "
                    + "For full safety, you should monitor head events and confirm the dependent root "
                    + "in this response matches:\n"
                    + "- event.current_duty_dependent_root when `compute_epoch_at_slot(event.slot) == epoch`\n"
                    + "- event.block otherwise\n\n"
                    + "The dependent_root value is `get_block_root_at_slot(state, compute_start_slot_at_epoch(epoch) - 1)` "
                    + "or the genesis block root in the case of underflow.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .pathParam(EPOCH_PARAMETER)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .response(SC_SERVICE_UNAVAILABLE, "Service unavailable", HTTP_ERROR_RESPONSE_TYPE)
            .build());
    this.validatorDataProvider = validatorDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get proposer duties",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      description =
          "Request beacon node to provide all validators that are scheduled to propose a block in the given epoch.\n\n"
              + "Duties should only need to be checked once per epoch, "
              + "however a chain reorganization could occur that results in a change of duties. "
              + "For full safety, you should monitor head events and confirm the dependent root "
              + "in this response matches:\n"
              + "- event.current_duty_dependent_root when `compute_epoch_at_slot(event.slot) == epoch`\n"
              + "- event.block otherwise\n\n"
              + "The dependent_root value is `get_block_root_at_slot(state, compute_start_slot_at_epoch(epoch) - 1)` "
              + "or the genesis block root in the case of underflow.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetProposerDutiesResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    if (!validatorDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
      request.respondError(SC_SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE);
      return;
    }

    final UInt64 epoch = request.getPathParameter(EPOCH_PARAMETER);
    SafeFuture<Optional<ProposerDuties>> future = validatorDataProvider.getProposerDuties(epoch);

    request.respondAsync(
        future.thenApply(
            maybeProposerDuties ->
                maybeProposerDuties
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(
                        () ->
                            AsyncApiResponse.respondWithError(
                                SC_SERVICE_UNAVAILABLE, "Service unavailable"))));
  }
}
