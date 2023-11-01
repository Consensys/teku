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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BROADCAST_VALIDATION;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.slotBasedSelector;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Locale;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BroadcastValidationParameter;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class PostBlockV2 extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/beacon/blocks";

  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;

  public PostBlockV2(
      final DataProvider dataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(createMetadata(spec, schemaDefinitionCache));
    this.validatorDataProvider = dataProvider.getValidatorDataProvider();
    this.syncDataProvider = dataProvider.getSyncDataProvider();
  }

  PostBlockV2(
      final ValidatorDataProvider validatorDataProvider,
      final SyncDataProvider syncDataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(createMetadata(spec, schemaDefinitionCache));
    this.validatorDataProvider = validatorDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  private static EndpointMetadata createMetadata(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("publishBlockV2")
        .summary("Publish a signed block")
        .description(
            """
            Instructs the beacon node to broadcast a newly signed beacon block to the beacon network, \
            to be included in the beacon chain. A success response (20x) indicates that the block \
            passed gossip validation and was successfully broadcast onto the network. \
            The beacon node is also expected to integrate the block into the state, but may broadcast it \
            before doing so, so as to aid timely delivery of the block. Should the block fail full \
            validation, a separate success response code (202) is used to indicate that the block was \
            successfully broadcast but failed integration. After Deneb, this additionally instructs \
            the beacon node to broadcast all given signed blobs. The broadcast behaviour may be adjusted via the \
            `broadcast_validation` query parameter.""")
        .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
        .queryParam(PARAMETER_BROADCAST_VALIDATION)
        .requestBodyType(
            getSchemaDefinitionForAllSupportedMilestones(
                schemaDefinitionCache,
                "SignedBlock",
                SchemaDefinitions::getSignedBlockContainerSchema,
                (blockContainer, milestone) ->
                    schemaDefinitionCache
                        .milestoneAtSlot(blockContainer.getSlot())
                        .equals(milestone)),
            context ->
                slotBasedSelector(
                    context.getBody(),
                    schemaDefinitionCache,
                    SchemaDefinitions::getSignedBlockContainerSchema),
            spec::deserializeSignedBlockContainer)
        .response(SC_OK, "Block has been successfully broadcast, validated and imported.")
        .response(
            SC_ACCEPTED,
            "Block has been successfully broadcast, but failed validation and has not been imported.")
        .withBadRequestResponse(Optional.of("Unable to parse request body."))
        .response(
            SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing.", HTTP_ERROR_RESPONSE_TYPE)
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    if (syncDataProvider.isSyncing()) {
      request.respondError(SC_SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE);
      return;
    }

    final Optional<BroadcastValidationParameter> maybeBroadcastValidation =
        request.getOptionalQueryParameter(PARAMETER_BROADCAST_VALIDATION);

    final BroadcastValidationLevel broadcastValidationLevel =
        maybeBroadcastValidation
            .map(
                broadcastValidation ->
                    BroadcastValidationLevel.valueOf(
                        broadcastValidation.toString().toUpperCase(Locale.ROOT)))
            .orElse(BroadcastValidationLevel.GOSSIP);

    final SignedBlockContainer requestBody = request.getRequestBody();

    request.respondAsync(
        validatorDataProvider
            .submitSignedBlock(requestBody, broadcastValidationLevel)
            .thenApply(
                result -> {
                  if (result.getRejectionReason().isEmpty()) {
                    return AsyncApiResponse.respondWithCode(SC_OK);
                  } else if (result
                      .getRejectionReason()
                      .get()
                      .equals(FailureReason.INTERNAL_ERROR.name())) {
                    return AsyncApiResponse.respondWithError(
                        SC_INTERNAL_SERVER_ERROR, result.getRejectionReason().get());
                  } else {
                    return AsyncApiResponse.respondWithCode(SC_ACCEPTED);
                  }
                }));
  }
}
