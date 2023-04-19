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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;

public class PostAttesterDuties extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/duties/attester/{epoch}";
  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;

  private static final SerializableTypeDefinition<AttesterDuty> ATTESTER_DUTY_TYPE =
      SerializableTypeDefinition.object(AttesterDuty.class)
          .name("AttesterDuty")
          .withField("pubkey", PUBLIC_KEY_TYPE, AttesterDuty::getPublicKey)
          .withField("validator_index", INTEGER_TYPE, AttesterDuty::getValidatorIndex)
          .withField("committee_index", INTEGER_TYPE, AttesterDuty::getCommitteeIndex)
          .withField("committee_length", INTEGER_TYPE, AttesterDuty::getCommitteeLength)
          .withField("committees_at_slot", INTEGER_TYPE, AttesterDuty::getCommitteesAtSlot)
          .withField(
              "validator_committee_index", INTEGER_TYPE, AttesterDuty::getValidatorCommitteeIndex)
          .withField("slot", UINT64_TYPE, AttesterDuty::getSlot)
          .build();

  private static final SerializableTypeDefinition<AttesterDuties> RESPONSE_TYPE =
      SerializableTypeDefinition.object(AttesterDuties.class)
          .name("GetAttesterDutiesResponse")
          .withField("dependent_root", BYTES32_TYPE, AttesterDuties::getDependentRoot)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, AttesterDuties::isExecutionOptimistic)
          .withField(
              "data",
              SerializableTypeDefinition.listOf(ATTESTER_DUTY_TYPE),
              AttesterDuties::getDuties)
          .build();

  public PostAttesterDuties(final DataProvider dataProvider) {
    this(dataProvider.getSyncDataProvider(), dataProvider.getValidatorDataProvider());
  }

  PostAttesterDuties(
      final SyncDataProvider syncDataProvider, final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postAttesterDuties")
            .summary("Get attester duties")
            .description(
                "Requests the beacon node to provide a set of attestation duties, "
                    + "which should be performed by validators, for a particular epoch. "
                    + "Duties should only need to be checked once per epoch, however a chain "
                    + "reorganization (of > MIN_SEED_LOOKAHEAD epochs) could occur, "
                    + "resulting in a change of duties. "
                    + "For full safety, you should monitor head events and confirm the dependent root in "
                    + "this response matches:\n"
                    + "- event.previous_duty_dependent_root when `compute_epoch_at_slot(event.slot) == epoch`\n"
                    + "- event.current_duty_dependent_root when `compute_epoch_at_slot(event.slot) + 1 == epoch`\n"
                    + "- event.block otherwise\n\n"
                    + "The dependent_root value is "
                    + "`get_block_root_at_slot(state, compute_start_slot_at_epoch(epoch - 1) - 1)` "
                    + "or the genesis block root in the case of underflow.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(DeserializableTypeDefinition.listOf(INTEGER_TYPE))
            .pathParam(EPOCH_PARAMETER)
            .response(SC_OK, "Success response", RESPONSE_TYPE)
            .withServiceUnavailableResponse()
            .build());
    this.validatorDataProvider = validatorDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    if (!validatorDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
      request.respondError(
          SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing and not serving requests.");
      return;
    }

    final UInt64 epoch = request.getPathParameter(EPOCH_PARAMETER);
    final List<Integer> requestBody = request.getRequestBody();
    final IntList indices = IntArrayList.toList(requestBody.stream().mapToInt(Integer::intValue));

    SafeFuture<Optional<AttesterDuties>> future =
        validatorDataProvider.getAttesterDuties(epoch, indices);

    request.respondAsync(
        future.thenApply(
            attesterDuties ->
                attesterDuties
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondServiceUnavailable())));
  }
}
