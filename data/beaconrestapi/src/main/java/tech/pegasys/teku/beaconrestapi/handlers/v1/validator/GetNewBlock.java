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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.GRAFFITI;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_V1_VALIDATOR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBLSSignature;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;

import com.google.common.base.Throwables;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetNewBlockResponse;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class GetNewBlock extends AbstractHandler implements Handler {
  public static final String ROUTE = "/eth/v1/validator/blocks/:slot";
  private final ValidatorDataProvider provider;

  public GetNewBlock(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = dataProvider.getValidatorDataProvider();
  }

  GetNewBlock(final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Produce unsigned block",
      tags = {TAG_V1_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      description =
          "Requests a beacon node to produce a valid block, which can then be signed by a validator.",
      pathParams = {
        @OpenApiParam(
            name = SLOT,
            description = "The slot for which the block should be proposed."),
      },
      queryParams = {
        @OpenApiParam(
            name = RANDAO_REVEAL,
            description = "`BLSSignature Hex` BLS12-381 signature for the current epoch.",
            required = true),
        @OpenApiParam(name = GRAFFITI, description = "`Bytes32 Hex` Graffiti.")
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetNewBlockResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    try {
      final Map<String, List<String>> queryParamMap = ctx.queryParamMap();
      final Map<String, String> pathParamMap = ctx.pathParamMap();

      final UInt64 slot = UInt64.valueOf(pathParamMap.get(SLOT));
      final BLSSignature randao = getParameterValueAsBLSSignature(queryParamMap, RANDAO_REVEAL);
      final Optional<Bytes32> graffiti =
          getOptionalParameterValueAsBytes32(queryParamMap, GRAFFITI);
      ctx.result(
          provider
              .getUnsignedBeaconBlockAtSlot(slot, randao, graffiti)
              .thenApplyChecked(
                  maybeBlock -> {
                    if (maybeBlock.isEmpty()) {
                      throw new ChainDataUnavailableException();
                    }
                    return jsonProvider.objectToJSON(new GetNewBlockResponse(maybeBlock.get()));
                  })
              .exceptionallyCompose(error -> handleError(ctx, error)));
    } catch (final IllegalArgumentException e) {
      ctx.status(SC_BAD_REQUEST);
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
    }
  }

  private Optional<Bytes32> getOptionalParameterValueAsBytes32(
      Map<String, List<String>> queryParamMap, final String key) {
    if (queryParamMap.containsKey(key)) {
      return Optional.of(getParameterValueAsBytes32(queryParamMap, key));
    } else {
      return Optional.empty();
    }
  }

  private SafeFuture<String> handleError(final Context ctx, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof IllegalArgumentException) {
      ctx.status(SC_BAD_REQUEST);
      return SafeFuture.of(() -> jsonProvider.objectToJSON(new BadRequest(error.getMessage())));
    }
    return SafeFuture.failedFuture(error);
  }
}
