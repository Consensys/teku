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

package tech.pegasys.artemis.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.artemis.api.schema.BeaconValidators.PAGE_SIZE_DEFAULT;
import static tech.pegasys.artemis.api.schema.BeaconValidators.PAGE_TOKEN_DEFAULT;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.getMaxAgeForBeaconState;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ACTIVE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_SIZE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_TOKEN;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsInt;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
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
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.api.schema.BeaconValidators;
import tech.pegasys.artemis.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.client.ChainDataUnavailableException;
import tech.pegasys.artemis.util.async.SafeFuture;

public class GetValidators extends AbstractHandler implements Handler {
  public static final String ROUTE = "/beacon/validators";

  private final ChainDataProvider chainDataProvider;

  public GetValidators(final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.chainDataProvider = chainDataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get validators that match the specified query.",
      tags = {TAG_BEACON},
      description =
          "Returns validator information.\n\n"
              + "Returns the first page of validators in the current epoch if you do not specify any parameters.",
      queryParams = {
        @OpenApiParam(name = EPOCH, description = "Epoch to query. Defaults to the current epoch."),
        @OpenApiParam(
            name = ACTIVE,
            description =
                "Only return validators that are active in the specified `epoch`. "
                    + "By default, returns inactive and active validators.\n\n"
                    + "**Note**: The field accepts any value to return active validators."),
        @OpenApiParam(
            name = PAGE_SIZE,
            description =
                "The amount of results to return per page. Defaults to "
                    + PAGE_SIZE_DEFAULT
                    + " results."),
        @OpenApiParam(
            name = PAGE_TOKEN,
            description = "Page number to return. Defaults to page " + PAGE_TOKEN_DEFAULT + ".")
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = BeaconValidators.class),
            description = "List of validator objects."),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    final Map<String, List<String>> parameters = ctx.queryParamMap();
    try {
      final boolean activeOnly = parameters.containsKey(ACTIVE);
      int pageSize =
          getPositiveIntegerValueWithDefaultIfNotSupplied(parameters, PAGE_SIZE, PAGE_SIZE_DEFAULT);
      int pageToken =
          getPositiveIntegerValueWithDefaultIfNotSupplied(
              parameters, PAGE_TOKEN, PAGE_TOKEN_DEFAULT);

      boolean isFinalized = false;
      final SafeFuture<Optional<BeaconState>> future;
      if (parameters.containsKey(EPOCH)) {
        UnsignedLong epoch = getParameterValueAsUnsignedLong(parameters, EPOCH);
        UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);
        isFinalized = chainDataProvider.isFinalized(slot);
        future = chainDataProvider.getStateAtSlot(slot);
      } else {
        Bytes32 blockRoot =
            chainDataProvider.getBestBlockRoot().orElseThrow(ChainDataUnavailableException::new);
        future = chainDataProvider.getStateByBlockRoot(blockRoot);
      }

      if (isFinalized) {
        this.handlePossiblyGoneResult(
            ctx, future, getResultProcessor(activeOnly, pageSize, pageToken));
      } else {
        this.handlePossiblyMissingResult(
            ctx, future, getResultProcessor(activeOnly, pageSize, pageToken));
      }
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private final ResultProcessor<BeaconState> getResultProcessor(
      final boolean activeOnly, final int pageSize, final int pageToken) {
    return (ctx, state) -> {
      final BeaconValidators result = new BeaconValidators(state, activeOnly, pageSize, pageToken);
      ctx.header(Header.CACHE_CONTROL, getMaxAgeForBeaconState(chainDataProvider, state));
      return Optional.of(jsonProvider.objectToJSON(result));
    };
  }

  private int getPositiveIntegerValueWithDefaultIfNotSupplied(
      final Map<String, List<String>> parameters, final String key, final int defaultValue)
      throws IllegalArgumentException {
    int intValue;
    if (!parameters.containsKey(key)) {
      return defaultValue;
    } else {
      intValue = getParameterValueAsInt(parameters, key);
      if (intValue < 0) {
        throw new IllegalArgumentException(
            String.format("%s must be a positive integer value", key));
      }
    }
    return intValue;
  }
}
