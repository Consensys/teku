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

import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetAttesterSlashingsResponse;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostAttesterSlashings extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/pool/attester_slashings";
  private final NodeDataProvider nodeDataProvider;

  public PostAttesterSlashings(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.nodeDataProvider = dataProvider.getNodeDataProvider();
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Submit attester slashing object",
      tags = {TAG_V1_BEACON},
      description =
          "Submits attester slashing object to node's pool and if passes validation node MUST broadcast it to network.",
      requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = AttesterSlashing.class)}),
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description =
                "Attester Slashing has been successfully validated, added to the pool, and broadcast."),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description =
                "Invalid attester slashing, it will never pass validation so it's rejected"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    final AttesterSlashing attesterSlashing =
        jsonProvider.jsonToObject(ctx.body(), AttesterSlashing.class);

    SafeFuture<InternalValidationResult> result =
        nodeDataProvider.postAttesterSlashing(attesterSlashing);
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    ctx.result(
        jsonProvider.objectToJSON(
            new GetAttesterSlashingsResponse(nodeDataProvider.getAttesterSlashings())));
  }
}
