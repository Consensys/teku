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

package tech.pegasys.teku.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.GRAFFITI;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBLSSignature;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;

import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedLong;
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
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.util.async.SafeFuture;

public class GetNewBlock implements Handler {
  public static final String ROUTE = "/validator/block";
  private final JsonProvider jsonProvider;
  private final ValidatorDataProvider provider;

  public GetNewBlock(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.provider = dataProvider.getValidatorDataProvider();
  }

  GetNewBlock(final ValidatorDataProvider provider, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Create and return an unsigned beacon block at the specified slot.",
      tags = {TAG_VALIDATOR},
      queryParams = {
        @OpenApiParam(
            name = SLOT,
            description = "`UnsignedLong` Slot in which to create the beacon block.",
            required = true),
        @OpenApiParam(
            name = RANDAO_REVEAL,
            description = "`BLSSignature Hex` BLS12-381 signature for the current epoch.",
            required = true),
        @OpenApiParam(name = GRAFFITI, description = "`Bytes32 Hex` Graffiti.")
      },
      description =
          "Create and return an unsigned beacon block at the specified slot. "
              + "The `randao_reveal` and `slot` must be provided to create the block.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = BeaconBlock.class),
            description = "`BeaconBlock` object for the specified slot."),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied")
      })
  @Override
  public void handle(@NotNull Context ctx) throws Exception {
    try {
      final Map<String, List<String>> queryParamMap = ctx.queryParamMap();
      BLSSignature randao = getParameterValueAsBLSSignature(queryParamMap, RANDAO_REVEAL);
      UnsignedLong slot = getParameterValueAsUnsignedLong(queryParamMap, SLOT);
      Optional<Bytes32> graffiti = getOptionalParameterValueAsBytes32(queryParamMap, GRAFFITI);
      ctx.result(
          provider
              .getUnsignedBeaconBlockAtSlot(slot, randao, graffiti)
              .thenApplyChecked(
                  maybeBlock -> {
                    if (maybeBlock.isEmpty()) {
                      throw new ChainDataUnavailableException();
                    }
                    return jsonProvider.objectToJSON(maybeBlock.get());
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
