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
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForSlot;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_VALIDATOR_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_VALIDATOR_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorsResponse;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class GetStateValidators extends AbstractHandler {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/beacon/states/:state_id/validators";

  private final ChainDataProvider provider;

  public GetStateValidators(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  GetStateValidators(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get validators from state",
      tags = {TAG_V1_BEACON, TAG_VALIDATOR_REQUIRED},
      description = "Returns filterable list of validators with their balance, status and index.",
      pathParams = {
        @OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION),
      },
      queryParams = {
        @OpenApiParam(
            name = PARAM_VALIDATOR_ID,
            description = PARAM_VALIDATOR_DESCRIPTION,
            isRepeatable = true)
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetStateValidatorsResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    try {
      final UInt64 slot = parseSlotParam(ctx);

      final List<Integer> validatorIndices = parseValidatorsParam(ctx);

      SafeFuture<Optional<GetStateValidatorsResponse>> future =
          provider
              .getValidatorsDetails(slot, validatorIndices)
              .thenApply(result -> result.map(GetStateValidatorsResponse::new));

      ctx.header(Header.CACHE_CONTROL, getMaxAgeForSlot(provider, slot));
      if (provider.isFinalized(slot)) {
        handlePossiblyGoneResult(ctx, future);
      } else {
        handlePossiblyMissingResult(ctx, future);
      }
    } catch (ChainDataUnavailableException ex) {
      LOG.trace(ex);
      ctx.status(SC_SERVICE_UNAVAILABLE);
    } catch (IllegalArgumentException ex) {
      LOG.trace(ex);
      ctx.status(SC_BAD_REQUEST);
      ctx.result(jsonProvider.objectToJSON(new BadRequest(ex.getMessage())));
    }
  }

  private List<Integer> parseValidatorsParam(final Context ctx) {
    return ListQueryParameterUtils.getParameterAsStringList(ctx.queryParamMap(), PARAM_VALIDATOR_ID)
        .stream()
        .flatMap(
            validatorParameter -> provider.validatorParameterToIndex(validatorParameter).stream())
        .collect(Collectors.toList());
  }

  private UInt64 parseSlotParam(final Context ctx) {
    return provider
        .stateParameterToSlot(ctx.pathParamMap().get(PARAM_STATE_ID))
        .orElseThrow(ChainDataUnavailableException::new);
  }
}
