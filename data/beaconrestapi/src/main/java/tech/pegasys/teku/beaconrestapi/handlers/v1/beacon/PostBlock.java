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

import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_ACCEPTED;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorBlockResult;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;

public class PostBlock implements Handler {
  public static final String ROUTE = "/eth/v1/beacon/blocks";

  private final JsonProvider jsonProvider;
  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;

  public PostBlock(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this.validatorDataProvider = dataProvider.getValidatorDataProvider();
    this.syncDataProvider = dataProvider.getSyncDataProvider();
    this.jsonProvider = jsonProvider;
  }

  PostBlock(
      final ValidatorDataProvider validatorDataProvider,
      final SyncDataProvider syncDataProvider,
      final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.validatorDataProvider = validatorDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Publish a signed block",
      tags = {TAG_V1_BEACON, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(content = {@OpenApiContent(from = SignedBeaconBlock.class)}),
      description =
          "Submit a signed beacon block to the beacon node to be imported."
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
            status = RES_INTERNAL_ERROR,
            description = "Beacon node experienced an internal error."),
        @OpenApiResponse(
            status = RES_SERVICE_UNAVAILABLE,
            description = "Beacon node is currently syncing.")
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      if (syncDataProvider.isSyncing()) {
        ctx.status(SC_SERVICE_UNAVAILABLE);
        ctx.result(BadRequest.serviceUnavailable(jsonProvider));
        return;
      }

      final SignedBeaconBlock signedBeaconBlock =
          jsonProvider.jsonToObject(ctx.body(), SignedBeaconBlock.class);

      ctx.result(
          validatorDataProvider
              .submitSignedBlock(signedBeaconBlock)
              .thenApplyChecked(
                  validatorBlockResult -> handleResponseContext(ctx, validatorBlockResult)));

    } catch (final JsonMappingException | JsonParseException ex) {
      ctx.status(SC_BAD_REQUEST);
      ctx.result(BadRequest.badRequest(jsonProvider, ex.getMessage()));
    } catch (final Exception ex) {
      ctx.status(SC_INTERNAL_SERVER_ERROR);
      ctx.result(BadRequest.internalError(jsonProvider, ex.getMessage()));
    }
  }

  private String handleResponseContext(
      final Context ctx, final ValidatorBlockResult validatorBlockResult) {
    ctx.status(validatorBlockResult.getResponseCode());
    if (validatorBlockResult.getResponseCode() == SC_ACCEPTED
        || validatorBlockResult.getResponseCode() == SC_OK) {
      return "";
    }
    return validatorBlockResult
        .getFailureReason()
        .map(
            reason ->
                BadRequest.serialize(jsonProvider, validatorBlockResult.getResponseCode(), reason))
        .orElse("");
  }
}
