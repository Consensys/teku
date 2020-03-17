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

package tech.pegasys.artemis.beaconrestapi.handlers.beacon;

import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import tech.pegasys.artemis.beaconrestapi.schema.ListingResponse;
import tech.pegasys.artemis.provider.JsonProvider;

public class BeaconListingHandler implements Handler {
  public static final String ROUTE = "/beacon";
  JsonProvider jsonProvider;

  public BeaconListingHandler(JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the routes available under the beacon category.",
      tags = {"Listings"},
      queryParams = {},
      description =
          "Returns the beacon chain block that matches the specified epoch, slot, or block root.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = ListingResponse.class, isArray = true))
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    List<ListingResponse> beaconRoutes = List.of(GetBlock.listing);
    ctx.result(jsonProvider.objectToJSON(beaconRoutes));
  }
}
