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

package tech.pegasys.teku.beaconrestapi.handlers.v1.config;

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_CONFIG;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.config.GetForkScheduleResponse;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.ForkManifest;

public class GetForkSchedule implements Handler {
  public static final String ROUTE = "/eth/v1/config/fork_schedule";
  private final ForkManifest forkManifest;
  private final JsonProvider jsonProvider;

  public GetForkSchedule(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getSpecProvider().getForkManifest(), jsonProvider);
  }

  GetForkSchedule(final ForkManifest forkManifest, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.forkManifest = forkManifest;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get scheduled forks",
      tags = {TAG_CONFIG},
      description = "Retrieve all scheduled upcoming forks this node is aware of.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetForkScheduleResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    ctx.result(jsonProvider.objectToJSON(new GetForkScheduleResponse(getForkSchedule())));
  }

  private List<Fork> getForkSchedule() {
    return forkManifest.getForkSchedule().stream().map(Fork::new).collect(Collectors.toList());
  }
}
