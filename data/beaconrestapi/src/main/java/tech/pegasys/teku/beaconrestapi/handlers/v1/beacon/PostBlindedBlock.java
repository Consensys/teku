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

import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getAvailableSchemaDefinitionForAllMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.slotBasedSelector;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.interfaces.SignedBlindedBlock;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlindedBlockContents;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public class PostBlindedBlock extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/blinded_blocks";

  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;

  public PostBlindedBlock(
      final DataProvider dataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(
        dataProvider.getValidatorDataProvider(),
        dataProvider.getSyncDataProvider(),
        spec,
        schemaDefinitionCache);
  }

  PostBlindedBlock(
      final ValidatorDataProvider validatorDataProvider,
      final SyncDataProvider syncDataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData(spec, schemaDefinitionCache));
    this.validatorDataProvider = validatorDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    if (syncDataProvider.isSyncing()) {
      request.respondError(SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing.");
      return;
    }

    Object requestBody = request.getRequestBody();

    if (requestBody instanceof SignedBeaconBlock) {
      request.respondAsync(
          validatorDataProvider
              .submitSignedBlindedBlock((SignedBeaconBlock) requestBody)
              .thenApply(this::respond));
    } else {
      request.respondAsync(
          validatorDataProvider
              .submitSignedBlindedBlockContents((SignedBlindedBlockContents) requestBody)
              .thenApply(this::respond));
    }
  }

  @NotNull
  private AsyncApiResponse respond(SendSignedBlockResult blockResult) {
    if (blockResult.getRejectionReason().isEmpty()) {
      return AsyncApiResponse.respondWithCode(SC_OK);
    } else if (blockResult
        .getRejectionReason()
        .get()
        .equals(BlockImportResult.FailureReason.INTERNAL_ERROR.name())) {
      return AsyncApiResponse.respondWithError(
          SC_INTERNAL_SERVER_ERROR,
          "An internal error occurred, check the server logs for more details.");
    } else {
      return AsyncApiResponse.respondWithCode(SC_ACCEPTED);
    }
  }

  private static EndpointMetadata getEndpointMetaData(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("publishBlindedBlock")
        .summary("Publish a signed blinded block")
        .description(
            "Submit a signed blinded beacon block to the beacon node to be imported."
                + " The beacon node performs the required validation.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .requestBodyType(
            getRequestBodyTypes(schemaDefinitionCache),
            json ->
                slotBasedSelector(
                    json,
                    schemaDefinitionCache,
                    schemaDefinitions ->
                        slot -> {
                          if (schemaDefinitionCache
                              .milestoneAtSlot(slot)
                              .isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
                            return schemaDefinitions
                                .toVersionDeneb()
                                .orElseThrow()
                                .getSignedBlindedBlockContentsSchema();
                          } else {
                            return schemaDefinitions.getSignedBlindedBeaconBlockSchema();
                          }
                        }),
            spec::deserializeSignedBlindedBlock)
        .response(SC_OK, "Block has been successfully broadcast, validated and imported.")
        .response(
            SC_ACCEPTED,
            "Block has been successfully broadcast, but failed validation and has not been imported.")
        .withBadRequestResponse(Optional.of("Unable to parse request body."))
        .response(
            SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing.", HTTP_ERROR_RESPONSE_TYPE)
        .build();
  }

  private static SerializableOneOfTypeDefinition<Object> getRequestBodyTypes(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableOneOfTypeDefinitionBuilder<Object> builder =
        new SerializableOneOfTypeDefinitionBuilder<>().description("Request successful");
    builder.withType(
        value -> value instanceof SignedBlindedBlock,
        signedBlindedBeaconBlockRequestBodyType(schemaDefinitionCache));
    builder.withType(
        value -> value instanceof SignedBlindedBlockContents,
        signedBlindedBlockContentsRequestBodyType(schemaDefinitionCache));
    return builder.build();
  }

  private static SerializableTypeDefinition<SignedBeaconBlock>
      signedBlindedBeaconBlockRequestBodyType(SchemaDefinitionCache schemaDefinitionCache) {
    return getSchemaDefinitionForAllMilestones(
        schemaDefinitionCache,
        "SignedBlindedBlock",
        SchemaDefinitions::getSignedBlindedBeaconBlockSchema,
        (block, milestone) ->
            schemaDefinitionCache.milestoneAtSlot(block.getSlot()).equals(milestone));
  }

  private static SerializableTypeDefinition<SignedBlindedBlockContents>
      signedBlindedBlockContentsRequestBodyType(SchemaDefinitionCache schemaDefinitionCache) {
    return getAvailableSchemaDefinitionForAllMilestones(
        schemaDefinitionCache,
        "SignedBlindedBlockContents",
        schemaDefinitions ->
            schemaDefinitions
                .toVersionDeneb()
                .map(SchemaDefinitionsDeneb::getSignedBlindedBlockContentsSchema),
        (block, milestone) ->
            schemaDefinitionCache
                .milestoneAtSlot(block.getSignedBeaconBlock().getSlot())
                .equals(milestone));
  }
}
