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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconBlockResponse;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class BeaconBlockHandler implements Handler {

  private final ChainStorageClient client;
  private final HistoricalChainData historicalChainData;
  public static final String ROUTE = "/beacon/block";
  private final JsonProvider jsonProvider;

  public BeaconBlockHandler(
      final ChainStorageClient client,
      final HistoricalChainData historicalChainData,
      final JsonProvider jsonProvider) {
    this.client = client;
    this.historicalChainData = historicalChainData;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Request that the node return a specified beacon chain block.",
      tags = {"Beacon"},
      queryParams = {
        @OpenApiParam(name = "epoch", description = "Query by epoch number (uint64)"),
        @OpenApiParam(name = "slot", description = "Query by slot number (uint64)"),
        @OpenApiParam(name = "root", description = "Query by tree hash root (Bytes32)")
      },
      description =
          "Request that the node return a beacon chain block that matches the provided criteria.",
      responses = {
        @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(from = BeaconBlockResponse.class)),
        @OpenApiResponse(status = "400", description = "Invalid parameters supplied"),
        @OpenApiResponse(status = "404", description = "Specified block not found")
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      if (ctx.queryParamMap().containsKey(ROOT)) {
        final Bytes32 root = Bytes32.fromHexString(validateParms(ctx, ROOT));
        if (client.getStore() != null) {
          final SignedBeaconBlock block = client.getStore().getSignedBlock(root);
          if (block != null) {
            ctx.result(jsonProvider.objectToJSON(new BeaconBlockResponse(block)));
            return;
          }
          ctx.status(SC_NOT_FOUND);
          return;
        }
      }

      final UnsignedLong slot;
      if (ctx.queryParamMap().containsKey(EPOCH)) {
        slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(validateParms(ctx, EPOCH)));
      } else if (ctx.queryParamMap().containsKey(SLOT)) {
        slot = UnsignedLong.valueOf(validateParms(ctx, SLOT));
      } else {
        throw new IllegalArgumentException(
            "Query parameter missing. Must specify one of root or epoch or slot.");
      }

      final Optional<SignedBeaconBlock> blockBySlot = getBlockBySlot(slot);
      if (blockBySlot.isPresent()) {
        ctx.result(jsonProvider.objectToJSON(new BeaconBlockResponse(blockBySlot.get())));
        return;
      }
      ctx.status(SC_NOT_FOUND);
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(SC_BAD_REQUEST, e.getMessage())));
    }
  }

  private Optional<SignedBeaconBlock> getBlockBySlot(final UnsignedLong slot) {
    return client
        .getBlockRootBySlot(slot)
        .map(root -> client.getStore().getSignedBlock(root))
        .or(() -> historicalChainData.getFinalizedBlockAtSlot(slot).join());
  }

  private String validateParms(final Context ctx, final String key) {
    if (ctx.queryParamMap().containsKey(key) && !StringUtils.isEmpty(ctx.queryParam(key))) {
      return ctx.queryParam(key);
    } else {
      throw new IllegalArgumentException(String.format("'%s' cannot be null or empty.", key));
    }
  }
}
