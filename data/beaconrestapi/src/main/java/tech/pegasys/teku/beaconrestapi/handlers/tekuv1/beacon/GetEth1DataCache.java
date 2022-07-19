/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.response.v1.teku.GetEth1DataCacheResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;

public class GetEth1DataCache extends MigratingEndpointAdapter {

  public static final String ROUTE = "/teku/v1/beacon/pool/eth1cache";

  private static final SerializableTypeDefinition<List<Eth1Data>> ETH1DATA_CACHE_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<Eth1Data>>object()
          .name("GetEth1DataCacheResponse")
          .withField(
              "data",
              SerializableTypeDefinition.listOf(Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition()),
              Function.identity())
          .build();

  private final Eth1DataCache eth1DataCache;

  public GetEth1DataCache(Eth1DataCache eth1DataCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getAllEth1DataCacheBlocks")
            .summary("Get all cached Eth1 blocks.")
            .description("Get all cached Eth1 blocks.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", ETH1DATA_CACHE_RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.eth1DataCache = eth1DataCache;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get all cached Eth1 blocks",
      description = "Get all cached Eth1 blocks.",
      tags = {TAG_TEKU},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetEth1DataCacheResponse.class)),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    List<Eth1Data> eth1CachedBlocks = this.eth1DataCache.getAllEth1Blocks();
    if (eth1CachedBlocks.isEmpty()) {
      request.respondWithCode(
          SC_NOT_FOUND, new HttpErrorResponse(SC_NOT_FOUND, "Eth1 blocks cache is empty"));
    } else {
      request.respondOk(eth1CachedBlocks);
    }
  }
}
