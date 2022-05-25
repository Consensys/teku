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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.debug.GetDepositsResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.validator.coordinator.DepositProvider;

@SuppressWarnings("unused")
public class GetDeposits extends MigratingEndpointAdapter {

  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/debug/beacon/deposits";
  private static final String DEFAULT_STATE_ID = "head";

  private static final SerializableTypeDefinition<List<Deposit>> DEPOSITS_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<Deposit>>object()
          .name("GetDepositsResponse")
          .withField(
              "data", listOf(Deposit.SSZ_SCHEMA.getJsonTypeDefinition()), Function.identity())
          .build();

  private final ChainDataProvider chainDataProvider;
  private final DepositProvider depositProvider;
  private final Spec spec;

  public GetDeposits(
      final DataProvider provider, final DepositProvider depositProvider, final Spec spec) {
    this(provider.getChainDataProvider(), depositProvider, spec);
  }

  public GetDeposits(
      final ChainDataProvider chainDataProvider,
      final DepositProvider depositProvider,
      final Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getDeposits")
            .summary("Deposits not yet included on the chain")
            .description(
                "Range of deposits that could be used in new block if we are assigned to be a proposer.")
            .tags(TAG_DEBUG)
            .response(SC_OK, "Request successful", DEPOSITS_RESPONSE_TYPE)
            .build());
    this.chainDataProvider = chainDataProvider;
    this.depositProvider = depositProvider;
    this.spec = spec;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Deposits not yet included on the chain",
      description =
          "Range of deposits that could be used in new block if we are assigned to be a proposer.",
      tags = {TAG_DEBUG},
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
    request.respondAsync(
        chainDataProvider
            .getBeaconState(DEFAULT_STATE_ID)
            .thenApplyChecked(
                maybeState -> {
                  if (maybeState.isEmpty()) {
                    return AsyncApiResponse.respondNotFound();
                  } else {
                    List<Deposit> allDeposits =
                        depositProvider.getAllPendingDeposits(maybeState.get().getData());
                    return AsyncApiResponse.respondOk(allDeposits);
                  }
                })
            .exceptionally(
                ex -> {
                  LOG.error("Failed to retrieve deposits", ex);
                  return AsyncApiResponse.respondWithError(
                      SC_INTERNAL_SERVER_ERROR,
                      "An internal error occurred, check the server logs for more details.");
                }));
  }
}
