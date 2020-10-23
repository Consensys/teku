/*
 * Copyright 2019 ConsenSys AG.
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
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForSignedBlock;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.EPOCH_QUERY_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.ROOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsEpoch;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUInt64;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class GetBlock implements Handler {

  public static final String ROUTE = "/beacon/block";
  static final String TOO_MANY_PARAMETERS =
      "Too many query parameters specified. Please supply only one.";
  static final String NO_PARAMETERS =
      "No parameters were provided; please supply slot, epoch, or root.";
  static final String NO_VALID_PARAMETER =
      "An invalid parameter was specified; please supply slot, epoch, or root.";
  private final JsonProvider jsonProvider;
  private final ChainDataProvider provider;

  public GetBlock(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary =
          "Get the beacon chain block matching the criteria.\n"
              + "Deprecated - use `/eth/v1/beacon/blocks/{block_id}` instead.",
      tags = {TAG_BEACON},
      queryParams = {
        @OpenApiParam(name = EPOCH, description = EPOCH_QUERY_DESCRIPTION),
        @OpenApiParam(name = SLOT, description = SLOT_QUERY_DESCRIPTION),
        @OpenApiParam(name = ROOT, description = ROOT_QUERY_DESCRIPTION)
      },
      description =
          "Returns the beacon chain block that matches the specified epoch, slot, or block root.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = GetBlockResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(status = RES_NOT_FOUND, description = "Specified block not found")
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      final Map<String, List<String>> queryParamMap = ctx.queryParamMap();
      if (queryParamMap.size() < 1) {
        throw new IllegalArgumentException(NO_PARAMETERS);
      } else if (queryParamMap.size() > 1) {
        throw new IllegalArgumentException(TOO_MANY_PARAMETERS);
      }

      if (queryParamMap.containsKey(ROOT)) {
        final Bytes32 blockRoot = getParameterValueAsBytes32(queryParamMap, ROOT);

        ctx.result(
            provider
                .getBlockByBlockRoot(blockRoot)
                .thenApplyChecked(block -> handleResponseContext(ctx, block)));
        return;
      }

      final UInt64 slot;
      if (queryParamMap.containsKey(EPOCH)) {
        UInt64 epoch = getParameterValueAsEpoch(queryParamMap, EPOCH);
        slot = compute_start_slot_at_epoch(epoch);
      } else if (queryParamMap.containsKey(SLOT)) {
        slot = getParameterValueAsUInt64(queryParamMap, SLOT);
      } else {
        throw new IllegalArgumentException(NO_VALID_PARAMETER);
      }

      ctx.result(
          provider
              .getBlockBySlot(slot)
              .thenApplyChecked(block -> handleResponseContext(ctx, block)));

    } catch (final IllegalArgumentException e) {
      ctx.status(SC_BAD_REQUEST);
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
    }
  }

  private String handleResponseContext(Context ctx, Optional<GetBlockResponse> blockOptional)
      throws JsonProcessingException {
    if (blockOptional.isPresent()) {
      GetBlockResponse response = blockOptional.get();
      ctx.header(
          Header.CACHE_CONTROL, getMaxAgeForSignedBlock(provider, response.signedBeaconBlock));
      return jsonProvider.objectToJSON(response);
    } else {
      ctx.status(SC_NOT_FOUND);
      return null;
    }
  }
}
