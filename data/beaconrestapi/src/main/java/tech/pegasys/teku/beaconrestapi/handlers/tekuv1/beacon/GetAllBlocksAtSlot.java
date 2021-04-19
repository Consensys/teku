/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.teku.GetAllBlocksAtSlotResponse;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetAllBlocksAtSlot implements Handler {
  public static final String ROUTE = "/teku/v1/beacon/blocks/:slot";
  private final ChainDataProvider chainDataProvider;
  private final JsonProvider jsonProvider;

  public GetAllBlocksAtSlot(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  public GetAllBlocksAtSlot(
      final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get blocks at slot",
      tags = {TAG_EXPERIMENTAL},
      description = "Get all blocks (canonical and non-canonical) by slot.",
      pathParams = {@OpenApiParam(name = SLOT, description = "slot of the blocks to retrieve.")},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetAllBlocksAtSlotResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    final Map<String, String> pathParamMap = ctx.pathParamMap();
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);

    SafeFuture<Set<SignedBeaconBlock>> future =
        chainDataProvider.getAllBlocksAtSlot(pathParamMap.get(SLOT));
    ctx.result(
        future.thenApplyChecked(
            result -> {
              if (result.isEmpty()) {
                ctx.status(SC_NOT_FOUND);
                return BadRequest.serialize(
                    jsonProvider, SC_NOT_FOUND, "Blocks not found: " + pathParamMap.get(SLOT));
              }
              final SpecMilestone milestone =
                  chainDataProvider.getMilestoneAtSlot(UInt64.valueOf(pathParamMap.get(SLOT)));
              return jsonProvider.objectToJSON(new GetAllBlocksAtSlotResponse(milestone, result));
            }));
  }
}
