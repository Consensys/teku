/*
 * Copyright 2021 ConsenSys AG.
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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.teku.GetEth1DataResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;

public class GetEth1Data extends MigratingEndpointAdapter {

  public static final String ROUTE = "/teku/v1/beacon/state/eth1data";

  private static final SerializableTypeDefinition<Eth1Data> ETH1DATA_RESPONSE_TYPE =
      SerializableTypeDefinition.<Eth1Data>object()
          .name("GetEth1DataResponse")
          .withField("data", Eth1Data.SSZ_SCHEMA.getJsonTypeDefinition(), Function.identity())
          .build();

  private final ChainDataProvider chainDataProvider;
  private final Eth1DataProvider eth1DataProvider;

  public GetEth1Data(final DataProvider dataProvider, final Eth1DataProvider eth1DataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getEth1Data")
            .summary("Get new Eth1Data")
            .description("Eth1Data we'd include in a new block if we are a proposer.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", ETH1DATA_RESPONSE_TYPE)
            .build());
    this.chainDataProvider = dataProvider.getChainDataProvider();
    this.eth1DataProvider = eth1DataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get new Eth1Data",
      description = "Eth1Data we'd include in a new block if we are a proposer.",
      tags = {TAG_TEKU},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetEth1DataResponse.class)),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        chainDataProvider
            .getBeaconStateAtHead()
            .thenApply(
                maybeStateAndMetadata -> {
                  if (maybeStateAndMetadata.isEmpty()) {
                    return AsyncApiResponse.respondNotFound();
                  } else {
                    return AsyncApiResponse.respondOk(
                        eth1DataProvider.getEth1Vote(maybeStateAndMetadata.get()));
                  }
                }));
  }
}
