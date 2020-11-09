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

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostProposerSlashing extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/pool/proposer_slashings";
  private final NodeDataProvider nodeDataProvider;

  public PostProposerSlashing(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getNodeDataProvider(), jsonProvider);
  }

  public PostProposerSlashing(final NodeDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.nodeDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Submit proposer slashing object",
      tags = {TAG_V1_BEACON},
      description =
          "Submits proposer slashing object to node's pool and if passes validation node MUST broadcast it to network.",
      requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = ProposerSlashing.class)}),
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description =
                "Proposer Slashing has been successfully validated, added to the pool, and broadcast."),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description =
                "Invalid proposer slashing, it will never pass validation so it's rejected"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      final ProposerSlashing proposerSlashing =
          jsonProvider.jsonToObject(ctx.body(), ProposerSlashing.class);
      InternalValidationResult result =
          nodeDataProvider.postProposerSlashing(proposerSlashing).join();
      if (result.equals(InternalValidationResult.IGNORE)
          || result.equals(InternalValidationResult.REJECT)) {
        ctx.status(SC_BAD_REQUEST);
        ctx.result(
            BadRequest.badRequest(
                jsonProvider,
                "Invalid proposer slashing, it will never pass validation so it's rejected"));
      } else {
        ctx.status(SC_OK);
      }
    } catch (final IllegalArgumentException | JsonMappingException e) {
      ctx.result(BadRequest.badRequest(jsonProvider, e.getMessage()));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
