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

import static tech.pegasys.teku.infrastructure.http.ContentTypes.JSON;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BLOCK_ID_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;

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
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetBlock extends AbstractHandler implements Handler {
  private static final String OAPI_ROUTE = "/eth/v2/beacon/blocks/:block_id";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private final ChainDataProvider chainDataProvider;

  public GetBlock(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  public GetBlock(final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Get block",
      tags = {TAG_BEACON},
      description = "Retrieves block details for given block id.",
      pathParams = {@OpenApiParam(name = PARAM_BLOCK_ID, description = PARAM_BLOCK_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = {
              @OpenApiContent(type = JSON, from = GetBlockResponseV2.class),
              @OpenApiContent(type = OCTET_STREAM)
            }),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    final Map<String, String> pathParams = ctx.pathParamMap();
    final Optional<String> maybeAcceptHeader = Optional.ofNullable(ctx.header(HEADER_ACCEPT));
    final String blockIdentifier = pathParams.get(PARAM_BLOCK_ID);

    if (getContentType(SSZ_OR_JSON_CONTENT_TYPES, maybeAcceptHeader)
        .equalsIgnoreCase(OCTET_STREAM)) {
      final SafeFuture<Optional<SszResponse>> future =
          chainDataProvider.getBlockSsz(blockIdentifier);
      handleOptionalSszResult(
          ctx, future, this::handleSszResult, this::resultFilename, SC_NOT_FOUND);

    } else {
      final SafeFuture<Optional<ObjectAndMetaData<SignedBeaconBlock>>> future =
          chainDataProvider.getBlock(blockIdentifier);
      handleOptionalResult(ctx, future, this::handleJsonResult, SC_NOT_FOUND);
    }
  }

  private Optional<String> handleJsonResult(
      Context ctx, final ObjectAndMetaData<SignedBeaconBlock> response)
      throws JsonProcessingException {
    final Version version = Version.fromMilestone(response.getMilestone());
    ctx.header(HEADER_CONSENSUS_VERSION, version.name());
    return Optional.of(
        jsonProvider.objectToJSON(
            new GetBlockResponseV2(version, response.isExecutionOptimistic(), response.getData())));
  }

  private String resultFilename(final SszResponse response) {
    return response.abbreviatedHash + ".ssz";
  }

  private Optional<ByteArrayInputStream> handleSszResult(
      final Context ctx, final SszResponse response) {
    ctx.header(HEADER_CONSENSUS_VERSION, response.version.name());
    return Optional.of(response.byteStream);
  }
}
