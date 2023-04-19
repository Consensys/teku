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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
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
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;

public class GetProposerDuties extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/duties/proposer/{epoch}";

  private static final SerializableTypeDefinition<ProposerDuty> PROPOSER_DUTY_TYPE =
      SerializableTypeDefinition.object(ProposerDuty.class)
          .withField("pubkey", PUBLIC_KEY_TYPE, ProposerDuty::getPublicKey)
          .withField("validator_index", INTEGER_TYPE, ProposerDuty::getValidatorIndex)
          .withField("slot", UINT64_TYPE, ProposerDuty::getSlot)
          .build();

  private static final SerializableTypeDefinition<ProposerDuties> RESPONSE_TYPE =
      SerializableTypeDefinition.object(ProposerDuties.class)
          .name("GetProposerDutiesResponse")
          .withField("dependent_root", BYTES32_TYPE, ProposerDuties::getDependentRoot)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ProposerDuties::isExecutionOptimistic)
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
