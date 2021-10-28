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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.debug.ChainHead;
import tech.pegasys.teku.api.response.v1.debug.GetChainHeadsResponse;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

public class GetChainHeads extends AbstractHandler implements Handler {
  public static final String ROUTE = "/eth/v1/debug/beacon/heads";
  private final ChainDataProvider chainDataProvider;

  public GetChainHeads(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  public GetChainHeads(final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get fork choice leaves",
      tags = {TAG_DEBUG},
      description = "Retrieves all possible chain heads (leaves of fork choice tree).",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetChainHeadsResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    final SafeFuture<Optional<List<ChainHead>>> future = chainDataProvider.getChainHeads();
    handleOptionalResult(ctx, future, this::handleResult, SC_NOT_FOUND);
  }

  private Optional<String> handleResult(Context ctx, final List<ChainHead> response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetChainHeadsResponse(response)));
  }
}
