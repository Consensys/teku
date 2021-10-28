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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.response.v1.node.Version;
import tech.pegasys.teku.api.response.v1.node.VersionResponse;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.provider.JsonProvider;

public class GetVersion implements Handler {
  private final JsonProvider jsonProvider;

  public GetVersion(JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/eth/v1/node/version";

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node version",
      description =
          "similar to [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3).",
      tags = {TAG_NODE},
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = VersionResponse.class))
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    Version v = new Version(VersionProvider.VERSION);
    VersionResponse response = new VersionResponse(v);
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    ctx.json(jsonProvider.objectToJSON(response));
  }
}
