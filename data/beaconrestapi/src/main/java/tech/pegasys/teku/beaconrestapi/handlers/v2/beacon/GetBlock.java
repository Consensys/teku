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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.HEADER_ACCEPT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.HEADER_ACCEPT_OCTET;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_BLOCK_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_BLOCK_ID_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.SszResponse;
import tech.pegasys.teku.api.response.v2.beacon.GetBlockResponseV2;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetBlock extends AbstractHandler implements Handler {
  public static final String ROUTE = "/eth/v2/beacon/blocks/:block_id";
  private final ChainDataProvider chainDataProvider;

  public GetBlock(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  public GetBlock(final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get block",
      tags = {TAG_EXPERIMENTAL},
      description = "Retrieves block details for given block id.",
      pathParams = {@OpenApiParam(name = PARAM_BLOCK_ID, description = PARAM_BLOCK_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetBlockResponseV2.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    final Map<String, String> pathParams = ctx.pathParamMap();
    final Optional<String> maybeAcceptHeader = Optional.ofNullable(ctx.header(HEADER_ACCEPT));
    final String blockIdentifier = pathParams.get(PARAM_BLOCK_ID);
    if (maybeAcceptHeader.orElse("").equalsIgnoreCase(HEADER_ACCEPT_OCTET)) {
      final SafeFuture<Optional<SszResponse>> future =
          chainDataProvider.getBlockSsz(blockIdentifier);
      handleOptionalSszResult(
          ctx, future, this::handleSszResult, this::resultFilename, SC_NOT_FOUND);

    } else {
      final SafeFuture<Optional<SignedBeaconBlock>> future =
          chainDataProvider.getBlockV2(blockIdentifier);
      handleOptionalResult(ctx, future, this::handleJsonResult, SC_NOT_FOUND);
    }
  }

  private Optional<String> handleJsonResult(Context ctx, final SignedBeaconBlock response)
      throws JsonProcessingException {
    final SpecMilestone milestone =
        chainDataProvider.getMilestoneAtSlot(response.getMessage().slot);
    return Optional.of(jsonProvider.objectToJSON(new GetBlockResponseV2(milestone, response)));
  }

  private String resultFilename(final SszResponse response) {
    return response.abbreviatedHash + ".ssz";
  }

  private Optional<ByteArrayInputStream> handleSszResult(
      final Context context, final SszResponse response) {
    return Optional.of(response.byteStream);
  }
}
