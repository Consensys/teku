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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static io.javalin.core.util.Header.CACHE_CONTROL;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.getMaxAgeForSignedBlock;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.getMaxAgeForSlot;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedLong;
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

import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.Committee;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconCommitteesHandler implements Handler {

  public static final String ROUTE = "/beacon/committees";

  private final ChainDataProvider provider;
  private final JsonProvider jsonProvider;

  public BeaconCommitteesHandler(ChainDataProvider provider, JsonProvider jsonProvider) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the committee assignments for a given epoch.",
      tags = {TAG_BEACON},
      queryParams = {
        @OpenApiParam(
            name = EPOCH,
            description = "The epoch for which committees will be returned.",
            required = true),
      },
      description = "Request a list of the committee assignments for a given epoch.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = Committee.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Missing a query parameter"),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    try {
      if (!provider.isStoreAvailable()) {
        ctx.status(SC_NO_CONTENT);
        return;
      }
      UnsignedLong epoch = getParameterValueAsUnsignedLong(ctx.queryParamMap(), EPOCH);
      ctx.result(provider.getCommitteesAtEpoch(epoch)
          .thenApplyChecked(committees -> handleResponseContext(ctx, committees, epoch)));

    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private String handleResponseContext(Context ctx, List<Committee> committees, UnsignedLong epoch) throws JsonProcessingException {
    UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);
    ctx.header(Header.CACHE_CONTROL, getMaxAgeForSlot(provider, slot));
    return jsonProvider.objectToJSON(committees);
  }
}
