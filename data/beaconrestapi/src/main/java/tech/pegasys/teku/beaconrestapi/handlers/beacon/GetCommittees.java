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

package tech.pegasys.teku.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForSlot;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH_QUERY_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsEpoch;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class GetCommittees extends AbstractHandler implements Handler {

  public static final String ROUTE = "/beacon/committees";

  private final ChainDataProvider provider;

  public GetCommittees(ChainDataProvider provider, JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      deprecated = true,
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the committee assignments for an epoch.",
      tags = {TAG_BEACON},
      queryParams = {
        @OpenApiParam(name = EPOCH, description = EPOCH_QUERY_DESCRIPTION, required = true),
      },
      description =
          "Returns committee assignments for each slot in a specified epoch.\n"
              + "Deprecated - use `/eth/v1/beacon/states/{state_id}/committees/{epoch}` instead.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = Committee.class, isArray = true)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Missing a query parameter"),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      UInt64 epoch = getParameterValueAsEpoch(ctx.queryParamMap(), EPOCH);
      final SafeFuture<Optional<List<Committee>>> future = provider.getCommitteesAtEpoch(epoch);
      UInt64 slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);
      ctx.header(Header.CACHE_CONTROL, getMaxAgeForSlot(provider, slot));
      if (provider.isFinalized(slot)) {
        handlePossiblyGoneResult(ctx, future);
      } else {
        handlePossiblyMissingResult(ctx, future);
      }
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }
}
