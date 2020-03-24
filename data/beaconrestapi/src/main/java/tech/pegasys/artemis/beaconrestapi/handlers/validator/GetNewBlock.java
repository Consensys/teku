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

package tech.pegasys.artemis.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBLSSignature;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;

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
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.api.ValidatorDataProvider;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconBlock;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainDataUnavailableException;
import tech.pegasys.artemis.util.async.SafeFuture;

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
      summary = "Create and return an unsigned `BeaconBlock` at a given slot.",
      tags = {TAG_VALIDATOR},
      queryParams = {
        @OpenApiParam(name = SLOT, description = "Slot to create the beacon block for."),
        @OpenApiParam(name = RANDAO_REVEAL, description = "Randao to use to create the block.")
      },
      description =
          "Create and return an unsigned `BeaconBlock` at a given slot. "
              + "Both the `randao_reveal` and `slot` must be provided so that a block can be produced.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = BeaconBlock.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied")
      })
  @Override
  public void handle(@NotNull Context ctx) throws Exception {
    try {
      final Map<String, List<String>> queryParamMap = ctx.queryParamMap();
      BLSSignature randao = getParameterValueAsBLSSignature(queryParamMap, RANDAO_REVEAL);
      UnsignedLong slot = getParameterValueAsUnsignedLong(queryParamMap, SLOT);
      ctx.result(
          provider
              .getUnsignedBeaconBlockAtSlot(slot, randao)
              .thenApplyChecked(jsonProvider::objectToJSON)
              .exceptionallyCompose(error -> handleError(ctx, error)));
    } catch (final IllegalArgumentException e) {
      ctx.status(SC_BAD_REQUEST);
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
    }
  }

  private SafeFuture<String> handleError(final Context ctx, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof ChainDataUnavailableException) {
      ctx.status(SC_NO_CONTENT);
      return SafeFuture.completedFuture("");
    } else if (rootCause instanceof IllegalArgumentException) {
      ctx.status(SC_BAD_REQUEST);
      return SafeFuture.of(() -> jsonProvider.objectToJSON(new BadRequest(error.getMessage())));
    }
    return SafeFuture.failedFuture(error);
  }
}
