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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.headerBasedSelectorWithSlotFallback;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class PostBlindedBlock extends AbstractPostBlock {
  public static final String ROUTE = "/eth/v1/beacon/blinded_blocks";

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
    super(validatorDataProvider, syncDataProvider, createMetadata(spec, schemaDefinitionCache));
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    if (syncDataProvider.isSyncing()) {
      request.respondError(SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing.");
      return;
    }

    final SignedBlockContainer requestBody = request.getRequestBody();

    request.respondAsync(
        validatorDataProvider
            .submitSignedBlindedBlock(requestBody, BroadcastValidationLevel.NOT_REQUIRED)
            .thenApply(this::processSendSignedBlockResult));
  }

  private static EndpointMetadata createMetadata(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("publishBlindedBlock")
        .summary("Publish a signed blinded block")
        .description(
            """
            Instructs the beacon node to use the components of the `SignedBlindedBeaconBlock` to construct and publish a \
            `SignedBeaconBlock` by swapping out the `transactions_root` for the corresponding full list of `transactions`. \
            The beacon node should broadcast a newly constructed `SignedBeaconBlock` to the beacon network, \
            to be included in the beacon chain. The beacon node is not required to validate the signed \
            `BeaconBlock`, and a successful response (20X) only indicates that the broadcast has been \
            successful. The beacon node is expected to integrate the new block into its state, and \
            therefore validate the block internally, however blocks which fail the validation are still \
            broadcast but a different status code is returned (202). Pre-Bellatrix, this endpoint will accept \
            a `SignedBeaconBlock`.""")
        .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
        .deprecated(true)
        .requestBodyType(
            getSchemaDefinitionForAllSupportedMilestones(
                schemaDefinitionCache,
                "SignedBlindedBlock",
                SchemaDefinitions::getSignedBlindedBlockContainerSchema,
                (blockContainer, milestone) ->
                    schemaDefinitionCache
                        .milestoneAtSlot(blockContainer.getSlot())
                        .equals(milestone)),
            context ->
                headerBasedSelectorWithSlotFallback(
                    context.getHeaders(),
                    context.getBody(),
                    schemaDefinitionCache,
                    SchemaDefinitions::getSignedBlindedBlockContainerSchema),
            spec::deserializeSignedBlindedBlockContainer)
        .header(
            ETH_CONSENSUS_VERSION_TYPE.withDescription(
                "Version of the block being submitted, if using SSZ encoding."))
        .response(SC_OK, "Block has been successfully broadcast, validated and imported.")
        .response(
            SC_ACCEPTED,
            "Block has been successfully broadcast, but failed validation and has not been imported.")
        .withBadRequestResponse(Optional.of("Unable to parse request body."))
        .response(
            SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing.", HTTP_ERROR_RESPONSE_TYPE)
        .response(
            SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
        .build();
  }
}
