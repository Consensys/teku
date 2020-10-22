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

import static io.javalin.core.util.Header.CACHE_CONTROL;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_BEACON;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.provider.JsonProvider;

public class GetHead implements Handler {
  public static final String ROUTE = "/beacon/head";
  private final JsonProvider jsonProvider;
  private final ChainDataProvider provider;

  public GetHead(ChainDataProvider provider, JsonProvider jsonProvider) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      deprecated = true,
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get information about the chain head.",
      tags = {TAG_BEACON},
      description =
          "Returns information about the head of the beacon chain from the node’s perspective.\n\n"
              + "To retrieve finalized and justified information, use "
              + GetChainHead.ROUTE
              + " instead.\n"
              + "Deprecated - use `/eth/v1/beacon/headers` instead.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = BeaconHead.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    ctx.header(CACHE_CONTROL, CACHE_NONE);
    final Optional<BeaconHead> result = provider.getBeaconHead();
    if (result.isPresent()) {
      ctx.result(jsonProvider.objectToJSON(result.get()));
    } else {
      ctx.status(SC_NO_CONTENT);
    }
  }
}
