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

package tech.pegasys.artemis.beaconrestapi.networkhandlers;

import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.CACHE_ONE_HOUR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_NETWORK;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.artemis.api.NetworkDataProvider;
import tech.pegasys.artemis.provider.JsonProvider;

public class ENRHandler implements Handler {
  public static final String ROUTE = "/network/enr";
  private final JsonProvider jsonProvider;
  private final NetworkDataProvider network;

  public ENRHandler(NetworkDataProvider network, JsonProvider jsonProvider) {
    this.network = network;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Requests the listening ENR address of the beacon node.",
      tags = {TAG_NETWORK},
      description =
          "Returns a base64 encoded string representing the beacon node listening address.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = String.class),
            description = "Base64 encoded ENR or an empty string if discovery v5 is not in use.")
      })
  @Override
  public void handle(Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_ONE_HOUR);
    ctx.result(jsonProvider.objectToJSON(network.getEnr().orElse("")));
  }
}
