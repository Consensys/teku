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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_STATE_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class GetStatePendingPartialWithdrawals extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/states/{state_id}/pending_partial_withdrawals";

  private final ChainDataProvider chainDataProvider;

  public GetStatePendingPartialWithdrawals(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  GetStatePendingPartialWithdrawals(
      final ChainDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPendingPartialWithdrawals")
            .summary("Get pending partial withdrawals from state")
            .description(
                "Returns pending partial withdrawals for state with given 'stateId'. Should return 400 if requested before electra.")
            .pathParam(PARAMETER_STATE_ID)
            .tags(TAG_BEACON)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                sszResponseType(),
                ETH_CONSENSUS_HEADER_TYPE)
            .withNotFoundResponse()
            .withUnsupportedMediaTypeResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {

    final SafeFuture<Optional<ObjectAndMetaData<SszList<PendingPartialWithdrawal>>>> future =
        chainDataProvider.getStatePendingPartialWithdrawals(
            request.getPathParameter(PARAMETER_STATE_ID));

    request.respondAsync(
        future.thenApply(
            maybeData ->
                maybeData
                    .map(
                        objectAndMetadata -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              objectAndMetadata.getMilestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(objectAndMetadata);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<List<PendingPartialWithdrawal>>>
      getResponseType(final SchemaDefinitionCache schemaDefinitionCache) {
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        schemaDefinitionCache
            .getSchemaDefinition(SpecMilestone.ELECTRA)
            .toVersionElectra()
            .orElseThrow();

    final SerializableTypeDefinition<PendingPartialWithdrawal> pendingPartialWithdrawalType =
        schemaDefinitionsElectra.getPendingPartialWithdrawalSchema().getJsonTypeDefinition();

    return SerializableTypeDefinition.<ObjectAndMetaData<List<PendingPartialWithdrawal>>>object()
        .name("GetPendingPartialWithdrawalsResponse")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
        .withField("data", listOf(pendingPartialWithdrawalType), ObjectAndMetaData::getData)
        .build();
  }
}
