/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ROOT_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateRoot extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/root";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<ObjectAndMetaData<Bytes32>> RESPONSE_TYPE =
      SerializableTypeDefinition.<ObjectAndMetaData<Bytes32>>object()
          .name("GetStateRootResponse")
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
          .withField("data", ROOT_TYPE, ObjectAndMetaData::getData)
          .build();

  public GetStateRoot(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  GetStateRoot(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getStateRoot")
            .summary("Get state root")
            .description(
                "Calculates HashTreeRoot for state with given 'state_id'. If stateId is root, same value will be returned.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_STATE_ID)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<Bytes32>>> future =
        chainDataProvider.getStateRoot(request.getPathParameter(PARAMETER_STATE_ID));
    request.respondAsync(
        future.thenApply(
            maybeRootAndMetaData ->
                maybeRootAndMetaData
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondNotFound())));
  }
}
