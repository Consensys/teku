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

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.CACHE_FINALIZED;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.CACHE_NONE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_NODE;

import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.provider.JsonProvider;

public class GenesisTimeHandler implements Handler {
  private final JsonProvider jsonProvider;
  public static final String ROUTE = "/node/genesis_time/";
  private final ChainDataProvider provider;

  public GenesisTimeHandler(ChainDataProvider provider, JsonProvider jsonProvider) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = GenesisTimeHandler.ROUTE,
      method = HttpMethod.GET,
      summary = "Get the genesis_time parameter from beacon node configuration.",
      tags = {TAG_NODE},
      description =
          "Requests the genesis_time parameter from the beacon node, which should be consistent across all beacon nodes that follow the same beacon chain.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = UnsignedLong.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    Optional<UnsignedLong> optionalResult = provider.getGenesisTime();
    if (optionalResult.isPresent()) {
      ctx.header(Header.CACHE_CONTROL, CACHE_FINALIZED);
      ctx.result(jsonProvider.objectToJSON(optionalResult.get()));
    } else {
      ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
      ctx.status(SC_NO_CONTENT);
    }
  }
}
