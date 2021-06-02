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

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.HEADER_ACCEPT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.HEADER_ACCEPT_JSON;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.HEADER_ACCEPT_OCTET;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_DEBUG;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
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
import tech.pegasys.teku.api.response.v1.debug.GetStateResponse;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetState extends AbstractHandler implements Handler {
  public static final String ROUTE = "/eth/v1/debug/beacon/states/:state_id";
  private final ChainDataProvider chainDataProvider;

  public GetState(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  public GetState(final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get state",
      tags = {TAG_DEBUG},
      description =
          "Returns full BeaconState object for given state_id.\n\n"
              + "Use Accept header to select `application/octet-stream` if SSZ response type is required.\n\n"
              + "__NOTE__: Only phase0 beacon state will be returned in JSON, use `/eth/v2/beacon/states/{state_id}` for altair.",
      pathParams = {@OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION)},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = {
              @OpenApiContent(type = HEADER_ACCEPT_JSON, from = GetStateResponse.class),
              @OpenApiContent(type = HEADER_ACCEPT_OCTET)
            }),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    final Optional<String> maybeAcceptHeader = Optional.ofNullable(ctx.header(HEADER_ACCEPT));
    final Map<String, String> pathParamMap = ctx.pathParamMap();
    if (maybeAcceptHeader.orElse("").equalsIgnoreCase(HEADER_ACCEPT_OCTET)) {
      final SafeFuture<Optional<SszResponse>> future =
          chainDataProvider.getBeaconStateSsz(pathParamMap.get(PARAM_STATE_ID));
      handleOptionalSszResult(
          ctx, future, this::handleSszResult, this::resultFilename, SC_NOT_FOUND);
    } else {
      // accept header is not octet, could be anything else, or even not set - our default return is
      // json.
      final SafeFuture<Optional<BeaconState>> future =
          chainDataProvider.getBeaconState(pathParamMap.get(PARAM_STATE_ID));
      handleOptionalResult(ctx, future, this::handleJsonResult, SC_NOT_FOUND);
    }
  }

  private String resultFilename(final SszResponse response) {
    return response.abbreviatedHash + ".ssz";
  }

  private Optional<ByteArrayInputStream> handleSszResult(
      final Context context, final SszResponse response) {
    return Optional.of(response.byteStream);
  }

  private Optional<String> handleJsonResult(Context ctx, final BeaconState response)
      throws JsonProcessingException {
    final SpecMilestone milestone = chainDataProvider.getMilestoneAtSlot(response.slot);
    if (!milestone.equals(SpecMilestone.PHASE0)) {
      ctx.status(SC_BAD_REQUEST);
      return Optional.of(
          BadRequest.badRequest(
              jsonProvider,
              String.format(
                  "Slot %s is not a phase0 slot, please fetch via /eth/v2/debug/states",
                  response.slot)));
    }
    return Optional.of(jsonProvider.objectToJSON(new GetStateResponse(milestone, response)));
  }
}
