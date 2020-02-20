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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.cli.VersionProvider;

public class VersionHandler implements Handler {

  public VersionHandler(JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/node/version/";
  private final JsonProvider jsonProvider;

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get version string of the running beacon node.",
      tags = {"Node"},
      description =
          "Requests that the beacon node identify information about its implementation in a format similar to a HTTP User-Agent field.",
      responses = {
        @OpenApiResponse(status = "200", content = @OpenApiContent(from = String.class)),
      })
  @Override
  public void handle(@NotNull Context ctx) throws Exception {
    ctx.result(jsonProvider.objectToJSON(VersionProvider.VERSION));
  }
}
