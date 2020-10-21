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

import static com.google.common.base.Preconditions.checkArgument;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForSlot;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_VALIDATOR_DESCRIPTION;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_BEACON;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorsResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.beaconrestapi.ParameterUtils;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class GetStateValidators extends AbstractHandler {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/beacon/states/:state_id/validators";

  private final StateValidatorsUtil stateValidatorsUtil = new StateValidatorsUtil();
  private final ChainDataProvider chainDataProvider;

  public GetStateValidators(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this(dataProvider.getChainDataProvider(), jsonProvider);
  }

  GetStateValidators(final ChainDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get validators from state",
      tags = {TAG_V1_BEACON},
      description = "Returns filterable list of validators with their balance, status and index.",
      pathParams = {
        @OpenApiParam(name = PARAM_STATE_ID, description = PARAM_STATE_ID_DESCRIPTION),
      },
      queryParams = {
        @OpenApiParam(
            name = PARAM_ID,
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
    final Map<String, String> pathParams = ctx.pathParamMap();
    try {
      chainDataProvider.requireStoreAvailable();
      String stateIdParam = pathParams.get(PARAM_STATE_ID);
      checkArgument(stateIdParam != null, "State_id argument could not be find.");

      final List<Integer> validatorIndices =
          stateValidatorsUtil.parseValidatorsParam(chainDataProvider, ctx);

      SafeFuture<Optional<List<ValidatorResponse>>> future;
      final Optional<Bytes32> maybeRoot = ParameterUtils.getPotentialRoot(stateIdParam);
      if (maybeRoot.isPresent()) {
        future =
            chainDataProvider.getValidatorsDetailsByStateRoot(maybeRoot.get(), validatorIndices);
        handleOptionalResult(ctx, future, this::handleResult, SC_NOT_FOUND);
      } else {
        final Optional<UInt64> maybeSlot = chainDataProvider.stateParameterToSlot(stateIdParam);
        if (maybeSlot.isEmpty()) {
          ctx.status(SC_NOT_FOUND);
          return;
        }
        UInt64 slot = maybeSlot.get();
        future = chainDataProvider.getValidatorsDetailsBySlot(slot, validatorIndices);
        ctx.header(Header.CACHE_CONTROL, getMaxAgeForSlot(chainDataProvider, slot));
        if (chainDataProvider.isFinalized(slot)) {
          handlePossiblyGoneResult(ctx, future, this::handleResult);
        } else {
          handlePossiblyMissingResult(ctx, future, this::handleResult);
        }
      }
    } catch (ChainDataUnavailableException ex) {
      LOG.trace(ex);
      ctx.status(SC_SERVICE_UNAVAILABLE);
      ctx.result(BadRequest.serviceUnavailable(jsonProvider));
    } catch (IllegalArgumentException ex) {
      LOG.trace(ex);
      ctx.status(SC_BAD_REQUEST);
      ctx.result(BadRequest.badRequest(jsonProvider, ex.getMessage()));
    }
  }

  private Optional<String> handleResult(Context ctx, final List<ValidatorResponse> response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetStateValidatorsResponse(response)));
  }
}
