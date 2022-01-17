/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.response.v1.teku.GetPreparedBeaconProposersResponse;
import tech.pegasys.teku.provider.JsonProvider;

public class GetPreparedBeaconProposers implements Handler {

  public static final String ROUTE = "/teku/v1/beacon/prepared_beacon_proposers";

  private final JsonProvider jsonProvider;
  private final NodeDataProvider nodeDataProvider;

  public GetPreparedBeaconProposers(final DataProvider provider, final JsonProvider jsonProvider) {
    this(provider.getNodeDataProvider(), jsonProvider);
  }

  GetPreparedBeaconProposers(
      final NodeDataProvider nodeDataProvider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.nodeDataProvider = nodeDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get current prepared beacon proposers",
      tags = {TAG_EXPERIMENTAL},
      description =
          "Get the current proposers information held by beacon node as result of prepare_beacon_proposer validator API calls. This API is considered unstable and the returned data format may change in the future.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetPreparedBeaconProposersResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);

    final GetPreparedBeaconProposersResponse response =
        new GetPreparedBeaconProposersResponse(nodeDataProvider.getPreparedBeaconProposers());
    ctx.result(jsonProvider.objectToJSON(response));
  }
}
