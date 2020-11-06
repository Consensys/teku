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

package tech.pegasys.teku.beaconrestapi.handlers.node;

import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_NETWORK;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.provider.JsonProvider;

public class GetAttestationsInPoolCount implements Handler {
  public static final String ROUTE = "/node/pending_attestation_count";
  private final NodeDataProvider nodeDataProvider;
  private final JsonProvider jsonProvider;

  public GetAttestationsInPoolCount(NodeDataProvider nodeDataProvider, JsonProvider jsonProvider) {
    this.nodeDataProvider = nodeDataProvider;
    this.jsonProvider = jsonProvider;
  }

  @OpenApi(
      deprecated = true,
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the number of attestations in the pool.",
      tags = {TAG_NETWORK},
      description =
          "Returns the number of pending attestations in the current pool.\n"
              + "Deprecated - use `/eth/v1/beacon/pool/attestations` instead.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = long.class),
            description = "Number of pending attestations in the pool."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    ctx.result(jsonProvider.objectToJSON(nodeDataProvider.getAttestationPoolSize()));
  }
}
