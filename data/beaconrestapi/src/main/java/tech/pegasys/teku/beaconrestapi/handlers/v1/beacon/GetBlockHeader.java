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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_BLOCK_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_BLOCK_ID_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.BlockHeader;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockHeaderResponse;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public class GetBlockHeader extends AbstractHandler implements Handler {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/beacon/headers/:block_id";
  private final ChainDataProvider chainDataProvider;

  public GetBlockHeader(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  public GetBlockHeader(
      final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get block header",
      tags = {TAG_V1_BEACON},
      description = "Retrieves block header for given block id.",
      pathParams = {@OpenApiParam(name = PARAM_BLOCK_ID, description = PARAM_BLOCK_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetBlockHeaderResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    final Map<String, String> pathParams = ctx.pathParamMap();
    try {
      final SafeFuture<Optional<BlockHeader>> future =
          chainDataProvider.getBlockHeaderByBlockId(pathParams.get(PARAM_BLOCK_ID));
      handleOptionalResult(ctx, future, this::handleResult, SC_NOT_FOUND);
    } catch (IllegalArgumentException ex) {
      LOG.trace(ex);
      ctx.status(SC_BAD_REQUEST);
      ctx.result(BadRequest.badRequest(jsonProvider, ex.getMessage()));
    }
  }

  private Optional<String> handleResult(Context ctx, final BlockHeader response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetBlockHeaderResponse(response)));
  }
}
