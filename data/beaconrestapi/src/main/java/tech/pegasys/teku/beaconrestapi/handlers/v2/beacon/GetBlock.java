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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getAvailableSchemaDefinitionForAllMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllMilestones;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetBlock extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/beacon/blocks/{block_id}";
  private final ChainDataProvider chainDataProvider;

  public GetBlock(
      final DataProvider dataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getChainDataProvider(), spec, schemaDefinitionCache);
  }

  public GetBlock(
      final ChainDataProvider chainDataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlockV2")
            .summary("Get block")
            .description("Retrieves block details for given block id.")
            .tags(TAG_BEACON)
            .pathParam(PARAMETER_BLOCK_ID)
            .response(
                SC_OK,
                "Request successful",
                getResponseTypes(schemaDefinitionCache),
                sszResponseType())
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> future =
        chainDataProvider.getBlock(request.getPathParameter(PARAMETER_BLOCK_ID));

    request.respondAsync(
        future.thenApply(
            maybeBlockAndMetaData ->
                maybeBlockAndMetaData
                    .map(
                        blockAndMetaData -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              Version.fromMilestone(blockAndMetaData.getMilestone()).name());
                          return AsyncApiResponse.respondOk(blockAndMetaData);
                        })
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableOneOfTypeDefinition<Object> getResponseTypes(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableOneOfTypeDefinitionBuilder<Object> builder =
        new SerializableOneOfTypeDefinitionBuilder<>().description("Request successful");
    builder.withType(
        value ->
            value instanceof ObjectAndMetaData
                && ((ObjectAndMetaData<?>) value).getData() instanceof SignedBeaconBlock,
        getSignedBeaconBlockResponseType(schemaDefinitionCache));
    builder.withType(
        value ->
            value instanceof ObjectAndMetaData
                && ((ObjectAndMetaData<?>) value).getData() instanceof SignedBlockContents,
        getSignedBlockContentsResponseType(schemaDefinitionCache));
    return builder.build();
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<SignedBeaconBlock>>
      getSignedBeaconBlockResponseType(SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<SignedBeaconBlock> signedBeaconBlockType =
        getSchemaDefinitionForAllMilestones(
            schemaDefinitionCache,
            "SignedBeaconBlock",
            SchemaDefinitions::getSignedBeaconBlockSchema,
            (signedBeaconBlock, milestone) ->
                schemaDefinitionCache
                    .milestoneAtSlot(signedBeaconBlock.getSlot())
                    .equals(milestone));

    return SerializableTypeDefinition.<ObjectAndMetaData<SignedBeaconBlock>>object()
        .name("GetBlockV2Response")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
        .withField("data", signedBeaconBlockType, ObjectAndMetaData::getData)
        .build();
  }

  private static SerializableTypeDefinition<ObjectAndMetaData<SignedBlockContents>>
      getSignedBlockContentsResponseType(SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<SignedBlockContents> signedBlockContentsType =
        getAvailableSchemaDefinitionForAllMilestones(
            schemaDefinitionCache,
            "SignedBlockContents",
            schemaDefinitions ->
                schemaDefinitions
                    .toVersionDeneb()
                    .map(SchemaDefinitionsDeneb::getSignedBlockContentsSchema),
            (signedBlockContents, milestone) ->
                schemaDefinitionCache
                    .milestoneAtSlot(signedBlockContents.getSignedBeaconBlock().getSlot())
                    .equals(milestone));

    return SerializableTypeDefinition.<ObjectAndMetaData<SignedBlockContents>>object()
        .name("GetBlockContentsV2Response")
        .withField("version", MILESTONE_TYPE, ObjectAndMetaData::getMilestone)
        .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
        .withField(FINALIZED, BOOLEAN_TYPE, ObjectAndMetaData::isFinalized)
        .withField("data", signedBlockContentsType, ObjectAndMetaData::getData)
        .build();
  }
}
