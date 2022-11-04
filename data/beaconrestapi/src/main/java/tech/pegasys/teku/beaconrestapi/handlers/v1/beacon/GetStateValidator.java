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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_VALIDATOR_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.StateValidatorData;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Validator;

public class GetStateValidator extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/validators/{validator_id}";

  private static final DeserializableTypeDefinition<ValidatorStatus> STATUS_TYPE =
      DeserializableTypeDefinition.enumOf(ValidatorStatus.class);

  public static final SerializableTypeDefinition<StateValidatorData> STATE_VALIDATOR_DATA_TYPE =
      SerializableTypeDefinition.object(StateValidatorData.class)
          .withField("index", UINT64_TYPE, StateValidatorData::getIndex)
          .withField("balance", UINT64_TYPE, StateValidatorData::getBalance)
          .withField("status", STATUS_TYPE, StateValidatorData::getStatus)
          .withField(
              "validator",
              Validator.SSZ_SCHEMA.getJsonTypeDefinition(),
              StateValidatorData::getValidator)
          .build();

  private static final SerializableTypeDefinition<ObjectAndMetaData<StateValidatorData>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<ObjectAndMetaData<StateValidatorData>>object()
              .name("GetStateValidatorResponse")
              .withField(
                  EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
              .withField("data", STATE_VALIDATOR_DATA_TYPE, ObjectAndMetaData::getData)
              .build();

  private final ChainDataProvider chainDataProvider;

  public GetStateValidator(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateValidator(final ChainDataProvider provider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateValidator")
            .summary("Get validator from state")
            .description("Retrieves data about the given peer.")
            .pathParam(PARAMETER_STATE_ID)
            .pathParam(PARAMETER_VALIDATOR_ID)
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = provider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<StateAndMetaData>> future =
        chainDataProvider.getBeaconStateAndMetadata(request.getPathParameter(PARAMETER_STATE_ID));

    request.respondAsync(
        future.thenApply(
            maybeStateAndMetadata -> {
              if (maybeStateAndMetadata.isEmpty()) {
                return AsyncApiResponse.respondNotFound();
              }

              final String validatorIdParam = request.getPathParameter(PARAMETER_VALIDATOR_ID);
              Optional<ObjectAndMetaData<StateValidatorData>> response =
                  chainDataProvider.getStateValidator(
                      maybeStateAndMetadata.get(), validatorIdParam);

              return response
                  .map(AsyncApiResponse::respondOk)
                  .orElseGet(AsyncApiResponse::respondNotFound);
            }));
  }
}
