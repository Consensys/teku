/*
 * Copyright 2020 ConsenSys AG.
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

import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllMilestones;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_UNSUPPORTED_MEDIA_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.interfaces.SignedBlindedBlock;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public class PostBlindedBlock extends MigratingEndpointAdapter {
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

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Publish a signed blinded block",
      tags = {TAG_BEACON, TAG_VALIDATOR_REQUIRED, TAG_EXPERIMENTAL},
      requestBody =
          @OpenApiRequestBody(
              content = {
                @OpenApiContent(from = SignedBlindedBlock.class),
                @OpenApiContent(type = OCTET_STREAM)
              }),
      description =
          "Submit a signed blinded beacon block to the beacon node to be imported."
              + " The beacon node performs the required validation.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "Block has been successfully broadcast, validated and imported."),
        @OpenApiResponse(
            status = RES_ACCEPTED,
            description =
                "Block has been successfully broadcast, but failed validation and has not been imported."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Unable to parse request body."),
        @OpenApiResponse(
            status = RES_UNSUPPORTED_MEDIA_TYPE,
            description = "Unsupported media-type supplied in request body."),
        @OpenApiResponse(
            status = RES_INTERNAL_ERROR,
            description = "Beacon node experienced an internal error."),
        @OpenApiResponse(
            status = RES_SERVICE_UNAVAILABLE,
            description = "Beacon node is currently syncing.")
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    if (syncDataProvider.isSyncing()) {
      request.respondError(SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing.");
      return;
    }

    final SafeFuture<SendSignedBlockResult> result =
        validatorDataProvider.submitSignedBlindedBlock(request.getRequestBody());
    request.respondAsync(
        result.thenApply(
            blockResult -> {
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
            }));
  }

  private static EndpointMetadata getEndpointMetaData(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.post(ROUTE)
        .operationId("publishBlindedBlock")
        .summary("Publish a signed blinded block")
        .description(
            "Submit a signed blinded beacon block to the beacon node to be imported."
                + " The beacon node performs the required validation.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED, TAG_EXPERIMENTAL)
        .requestBodyType(
            getSchemaDefinitionForAllMilestones(
                schemaDefinitionCache,
                "SignedBlindedBlock",
                SchemaDefinitions::getSignedBlindedBeaconBlockSchema,
                (block, milestone) ->
                    schemaDefinitionCache.milestoneAtSlot(block.getSlot()).equals(milestone)),
            (json) ->
                MilestoneDependentTypesUtil.slotBasedSelector(
                    json,
                    schemaDefinitionCache,
                    SchemaDefinitions::getSignedBlindedBeaconBlockSchema),
            spec::deserializeSignedBeaconBlock)
        .response(SC_OK, "Block has been successfully broadcast, validated and imported.")
        .response(
            SC_ACCEPTED,
            "Block has been successfully broadcast, but failed validation and has not been imported.")
        .withBadRequestResponse(Optional.of("Unable to parse request body."))
        .response(
            SC_SERVICE_UNAVAILABLE, "Beacon node is currently syncing.", HTTP_ERROR_RESPONSE_TYPE)
        .build();
  }
}
