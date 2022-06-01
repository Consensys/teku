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
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.response.v1.teku.GetDepositsResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.validator.coordinator.DepositProvider;

public class GetDeposits extends MigratingEndpointAdapter {

  public static final String ROUTE = "/teku/v1/beacon/pool/deposits";

  private static final SerializableTypeDefinition<DepositWithIndex> DEPOSIT_WITH_INDEX_TYPE =
      SerializableTypeDefinition.object(DepositWithIndex.class)
          .withField("index", CoreTypes.UINT64_TYPE, DepositWithIndex::getIndex)
          .withField(
              "data", DepositData.SSZ_SCHEMA.getJsonTypeDefinition(), DepositWithIndex::getData)
          .build();

  public static final SerializableTypeDefinition<List<DepositWithIndex>> DEPOSITS_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<DepositWithIndex>>object()
          .name("GetDepositsResponse")
          .withField("data", listOf(DEPOSIT_WITH_INDEX_TYPE), Function.identity())
          .build();

  private final DepositProvider depositProvider;

  public GetDeposits(final DepositProvider depositProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getDeposits")
            .summary("Get deposits")
            .description("Get all deposits currently held for inclusion in future blocks.")
            .tags(TAG_TEKU)
            .response(SC_OK, "Request successful", DEPOSITS_RESPONSE_TYPE)
            .build());
    this.depositProvider = depositProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get deposits",
      description = "Get all deposits currently held for inclusion in future blocks.",
      tags = {TAG_TEKU},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetDepositsResponse.class)),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondOk(depositProvider.getAvailableDeposits());
  }
}
