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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DRAFT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EIP_7732;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.metadata.ExecutionPayloadAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class GetExecutionPayload extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/beacon/execution_payload/{block_id}";
  private final ChainDataProvider chainDataProvider;

  public GetExecutionPayload(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetExecutionPayload(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getExecutionPayload")
            .summary("Get execution payload")
            .description("Retrieves execution payload details for a given block id.")
            .tags(TAG_DRAFT, TAG_EIP_7732)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                sszResponseType())
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ExecutionPayloadAndMetaData>> future =
        chainDataProvider.getExecutionPayload(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeExecutionPayloadAndMetaData ->
                maybeExecutionPayloadAndMetaData
                    .map(
                        executionPayloadAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              Version.fromMilestone(executionPayloadAndMetaData.getMilestone())
                                  .name());
                          return AsyncApiResponse.respondOk(executionPayloadAndMetaData);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<ExecutionPayloadAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<SignedExecutionPayloadEnvelope>
        signedExecutionPayloadEnvelopeType =
            getSchemaDefinitionForAllSupportedMilestones(
                schemaDefinitionCache,
                "SignedExecutionPayloadEnvelope",
                __ ->
                    SchemaDefinitionsEip7732.required(
                            schemaDefinitionCache.getSchemaDefinition(SpecMilestone.EIP7732))
                        .getSignedExecutionPayloadEnvelopeSchema(),
                (signedExecutionPayloadEnvelope, milestone) ->
                    schemaDefinitionCache
                        .milestoneAtSlot(signedExecutionPayloadEnvelope.getMessage().getSlot())
                        .equals(milestone));

    return SerializableTypeDefinition.<ExecutionPayloadAndMetaData>object()
        .name("GetExecutionPayloadResponse")
        .withField("version", MILESTONE_TYPE, ExecutionPayloadAndMetaData::getMilestone)
        .withField(
            EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ExecutionPayloadAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ExecutionPayloadAndMetaData::isFinalized)
        .withField("data", signedExecutionPayloadEnvelopeType, ExecutionPayloadAndMetaData::getData)
        .build();
  }
}
