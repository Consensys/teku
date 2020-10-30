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

import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetVoluntaryExitsResponse;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.provider.JsonProvider;

public class GetVoluntaryExits extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/pool/voluntary_exits";
  private final NodeDataProvider nodeDataProvider;

  public GetVoluntaryExits(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.nodeDataProvider = dataProvider.getNodeDataProvider();
  }

  GetVoluntaryExits(final NodeDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.nodeDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get signed voluntary exits",
      tags = {TAG_V1_BEACON},
      description =
          "Retrieves voluntary exits known by the node but not necessarily incorporated into any block.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetVoluntaryExitsResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    List<SignedVoluntaryExit> exits = nodeDataProvider.getVoluntaryExits();
    ctx.result(jsonProvider.objectToJSON(new GetVoluntaryExitsResponse(exits)));
  }
}
