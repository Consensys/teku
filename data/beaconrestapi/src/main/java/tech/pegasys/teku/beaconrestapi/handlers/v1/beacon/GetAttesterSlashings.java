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

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetAttesterSlashingsResponse;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.provider.JsonProvider;

public class GetAttesterSlashings extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/beacon/pool/attester_slashings";
  private final NodeDataProvider nodeDataProvider;

  public GetAttesterSlashings(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.nodeDataProvider = dataProvider.getNodeDataProvider();
  }

  GetAttesterSlashings(final NodeDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.nodeDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get AttesterSlashings",
      tags = {TAG_BEACON},
      description =
          "Retrieves attester slashings known by the node but not necessarily incorporated into any block.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetAttesterSlashingsResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    List<AttesterSlashing> attesterSlashings = nodeDataProvider.getAttesterSlashings();
    ctx.json(jsonProvider.objectToJSON(new GetAttesterSlashingsResponse(attesterSlashings)));
  }
}
