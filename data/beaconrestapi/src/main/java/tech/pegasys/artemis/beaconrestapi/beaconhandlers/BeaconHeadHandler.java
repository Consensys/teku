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

import static io.javalin.core.util.Header.CACHE_CONTROL;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BeaconHead;
import tech.pegasys.artemis.provider.JsonProvider;

public class BeaconHeadHandler implements Handler {
  public static final String ROUTE = "/beacon/head";
  private final JsonProvider jsonProvider;
  private final ChainDataProvider provider;

  public BeaconHeadHandler(ChainDataProvider provider, JsonProvider jsonProvider) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the head of the beacon chain from the node's perspective.",
      tags = {TAG_BEACON},
      description =
          "Returns information about the head of the beacon chain from the node’s perspective.\n\nTo retrieve finalized and justified information use /beacon/chainhead instead.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = BeaconHead.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    ctx.header(CACHE_CONTROL, CACHE_NONE);
    Optional<BeaconHead> optionalResult = provider.getBeaconHead();

    if (optionalResult.isEmpty()) {
      ctx.status(SC_NO_CONTENT);
      return;
    }
    ctx.result(jsonProvider.objectToJSON(optionalResult.get()));
  }
}
