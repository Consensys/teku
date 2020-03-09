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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.getMaxAgeForSignedBlock;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

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
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.provider.JsonProvider;

public class BeaconBlockHandler implements Handler {

  public static final String ROUTE = "/beacon/block";
  static final String TOO_MANY_PARAMETERS =
      "Too many query parameters specified. Please supply only one.";
  static final String NO_PARAMETERS =
      "No parameters were provided; please supply slot, epoch, or root.";
  static final String NO_VALID_PARAMETER =
      "An invalid parameter was specified; please supply slot, epoch, or root.";
  private final JsonProvider jsonProvider;
  private final ChainDataProvider provider;

  public BeaconBlockHandler(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Request that the node return the specified beacon chain block.",
      tags = {"Beacon"},
      queryParams = {
        @OpenApiParam(name = EPOCH, description = "Query by epoch number (uint64)"),
        @OpenApiParam(name = SLOT, description = "Query by slot number (uint64)"),
        @OpenApiParam(name = ROOT, description = "Query by tree hash root (Bytes32)")
      },
      description =
          "Request that the node return the beacon chain block that matches the provided criteria.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = SignedBeaconBlock.class)),
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
        ;
        return;
      }

      final UnsignedLong slot;
      if (queryParamMap.containsKey(EPOCH)) {
        UnsignedLong epoch = getParameterValueAsUnsignedLong(queryParamMap, EPOCH);
        slot = compute_start_slot_at_epoch(epoch);
      } else if (queryParamMap.containsKey(SLOT)) {
        slot = getParameterValueAsUnsignedLong(queryParamMap, SLOT);
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

  private String handleResponseContext(Context ctx, Optional<SignedBeaconBlock> blockOptional)
      throws JsonProcessingException {
    if (blockOptional.isPresent()) {
      SignedBeaconBlock signedBeaconBlock = blockOptional.get();
      ctx.header(Header.CACHE_CONTROL, getMaxAgeForSignedBlock(provider, signedBeaconBlock));
      return jsonProvider.objectToJSON(signedBeaconBlock);
    } else {
      ctx.status(SC_NOT_FOUND);
      return null;
    }
  }
}
