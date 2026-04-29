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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.SIGNATURE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.executionPayloadAndMetaDataSszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.metadata.ExecutionPayloadAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetExecutionPayloadEnvelope extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/beacon/execution_payload_envelope/{block_id}";

  private final ChainDataProvider chainDataProvider;

  public GetExecutionPayloadEnvelope(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), schemaDefinitionCache);
  }

  public GetExecutionPayloadEnvelope(
      final ChainDataProvider chainDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getSignedExecutionPayloadEnvelope")
            .summary("Get signed execution payload envelope")
            .description("Retrieves signed execution payload envelope for a given block id.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(
                SC_OK,
                "Request successful",
                getResponseType(schemaDefinitionCache),
                executionPayloadAndMetaDataSszResponseType())
            .withNotFoundResponse()
            .withChainDataResponses()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ExecutionPayloadAndMetaData>> future =
        chainDataProvider.getExecutionPayloadEnvelope(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeExecutionPayloadEnvelopeAndMetaData ->
                maybeExecutionPayloadEnvelopeAndMetaData
                    .map(
                        executionPayloadEnvelopeAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              executionPayloadEnvelopeAndMetaData.milestone().lowerCaseName());
                          return AsyncApiResponse.respondOk(executionPayloadEnvelopeAndMetaData);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<ExecutionPayloadAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableOneOfTypeDefinition<ExecutionPayload> payloadType =
        buildOneOfPerMilestone(
            schemaDefinitionCache,
            "ExecutionPayload",
            milestone ->
                SchemaDefinitionsGloas.required(
                        schemaDefinitionCache.getSchemaDefinition(milestone))
                    .getExecutionPayloadSchema()
                    .getJsonTypeDefinition());
    final SerializableOneOfTypeDefinition<ExecutionRequests> executionRequestsType =
        buildOneOfPerMilestone(
            schemaDefinitionCache,
            "ExecutionRequests",
            milestone ->
                SchemaDefinitionsGloas.required(
                        schemaDefinitionCache.getSchemaDefinition(milestone))
                    .getExecutionRequestsSchema()
                    .getJsonTypeDefinition());

    final SerializableTypeDefinition<ExecutionPayloadAndMetaData> messageType =
        SerializableTypeDefinition.<ExecutionPayloadAndMetaData>object()
            .name("ExecutionPayloadEnvelope")
            .withField("payload", payloadType, m -> m.data().getMessage().getPayload())
            .withField(
                "execution_requests",
                executionRequestsType,
                m -> m.data().getMessage().getExecutionRequests())
            .withField("builder_index", UINT64_TYPE, m -> m.data().getMessage().getBuilderIndex())
            .withField(
                "beacon_block_root", BYTES32_TYPE, m -> m.data().getMessage().getBeaconBlockRoot())
            .withField("slot", UINT64_TYPE, m -> m.data().getMessage().getSlot())
            .withField("state_root", BYTES32_TYPE, ExecutionPayloadAndMetaData::stateRoot)
            .build();

    final SerializableTypeDefinition<ExecutionPayloadAndMetaData> signedEnvelopeType =
        SerializableTypeDefinition.<ExecutionPayloadAndMetaData>object()
            .name("SignedExecutionPayloadEnvelope")
            .withField("message", messageType, Function.identity())
            .withField("signature", SIGNATURE_TYPE, m -> m.data().getSignature())
            .build();

    return SerializableTypeDefinition.<ExecutionPayloadAndMetaData>object()
        .name("GetExecutionPayloadEnvelopeResponse")
        .withField("version", MILESTONE_TYPE, ExecutionPayloadAndMetaData::milestone)
        .withField(
            EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ExecutionPayloadAndMetaData::executionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ExecutionPayloadAndMetaData::finalized)
        .withField("data", signedEnvelopeType, Function.identity())
        .build();
  }

  private static <T> SerializableOneOfTypeDefinition<T> buildOneOfPerMilestone(
      final SchemaDefinitionCache schemaDefinitionCache,
      final String title,
      final Function<SpecMilestone, DeserializableTypeDefinition<? extends T>>
          jsonTypeForMilestone) {
    final SerializableOneOfTypeDefinitionBuilder<T> builder =
        new SerializableOneOfTypeDefinitionBuilder<T>().title(title);
    for (final SpecMilestone milestone : schemaDefinitionCache.getSupportedMilestones()) {
      if (milestone.isLessThan(SpecMilestone.GLOAS)) {
        continue;
      }
      builder.withType(__ -> true, jsonTypeForMilestone.apply(milestone));
    }
    return builder.build();
  }
}
