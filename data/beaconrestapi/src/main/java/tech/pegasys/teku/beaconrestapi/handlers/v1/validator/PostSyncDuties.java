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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;

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
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;

public class PostSyncDuties extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/duties/sync/{epoch}";
  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;

  private static final SerializableTypeDefinition<SyncCommitteeDuty> SYNC_COMMITTEE_DUTY_TYPE =
      SerializableTypeDefinition.object(SyncCommitteeDuty.class)
          .withField("pubkey", PUBLIC_KEY_TYPE, SyncCommitteeDuty::getPublicKey)
          .withField("validator_index", INTEGER_TYPE, SyncCommitteeDuty::getValidatorIndex)
          .withField(
              "validator_sync_committee_indices",
              SerializableTypeDefinition.listOf(INTEGER_TYPE),
              syncCommitteeDuty ->
                  new IntArrayList(syncCommitteeDuty.getValidatorSyncCommitteeIndices()))
          .build();

  private static final SerializableTypeDefinition<SyncCommitteeDuties> RESPONSE_TYPE =
      SerializableTypeDefinition.object(SyncCommitteeDuties.class)
          .name("GetSyncCommitteeDutiesResponse")
          .withField(
              "execution_optimistic", BOOLEAN_TYPE, SyncCommitteeDuties::isExecutionOptimistic)
          .withField(
              "data",
              SerializableTypeDefinition.listOf(SYNC_COMMITTEE_DUTY_TYPE),
              SyncCommitteeDuties::getDuties)
          .build();

  public PostSyncDuties(final DataProvider dataProvider) {
    this(dataProvider.getSyncDataProvider(), dataProvider.getValidatorDataProvider());
  }

  PostSyncDuties(
      final SyncDataProvider syncDataProvider, final ValidatorDataProvider validatorDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postSyncDuties")
            .summary("Get sync committee duties")
            .description("Requests the beacon node to provide a set of sync committee duties")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .pathParam(EPOCH_PARAMETER)
            .requestBodyType(DeserializableTypeDefinition.listOf(INTEGER_TYPE))
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withServiceUnavailableResponse()
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
    final List<Integer> requestBody = request.getRequestBody();
    final IntList indices = IntArrayList.toList(requestBody.stream().mapToInt(Integer::intValue));

    SafeFuture<Optional<SyncCommitteeDuties>> future =
        validatorDataProvider.getSyncDuties(epoch, indices);

    request.respondAsync(
        future.thenApply(
            maybeSyncCommitteeDuties ->
                maybeSyncCommitteeDuties
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondServiceUnavailable())));
  }
}
