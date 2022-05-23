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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARENT_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_QUERY_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARENT_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.BlockHeaderData;
import tech.pegasys.teku.api.migrated.BlockHeadersResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockHeadersResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetBlockHeaders extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/beacon/headers";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<BlockHeadersResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(BlockHeadersResponse.class)
          .name("GetBlockHeadersResponse")
          .withField(
              "execution_optimistic", BOOLEAN_TYPE, BlockHeadersResponse::isExecutionOptimistic)
          .withField(
              "data",
              listOf(BlockHeaderData.getJsonTypeDefinition()),
              BlockHeadersResponse::getData)
          .build();

  public GetBlockHeaders(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetBlockHeaders(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlockHeaders")
            .summary("Get block headers")
            .description(
                "Retrieves block headers matching given query. By default it will fetch current head slot blocks.")
            .tags(TAG_BEACON)
            .queryParam(SLOT_QUERY_PARAMETER)
            .queryParam(PARENT_ROOT_PARAMETER)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get block headers",
      tags = {TAG_BEACON},
      description =
          "Retrieves block headers matching given query. By default it will fetch current head slot blocks.",
      queryParams = {
        @OpenApiParam(name = SLOT),
        @OpenApiParam(name = PARENT_ROOT, description = "Not currently supported.")
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetBlockHeadersResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final Optional<Bytes32> parentRoot = request.getOptionalQueryParameter(PARENT_ROOT_PARAMETER);
    final Optional<UInt64> slot = request.getOptionalQueryParameter(SLOT_QUERY_PARAMETER);

    final SafeFuture<BlockHeadersResponse> future =
        chainDataProvider.getBlockHeaders(parentRoot, slot);

    request.respondAsync(
        future
            .thenApplyChecked(AsyncApiResponse::respondOk)
            .exceptionallyCompose(
                error -> {
                  final Throwable rootCause = Throwables.getRootCause(error);
                  if (rootCause instanceof IllegalArgumentException) {
                    return SafeFuture.of(
                        () ->
                            AsyncApiResponse.respondWithError(SC_BAD_REQUEST, error.getMessage()));
                  }
                  return SafeFuture.failedFuture(error);
                }));
  }
}
