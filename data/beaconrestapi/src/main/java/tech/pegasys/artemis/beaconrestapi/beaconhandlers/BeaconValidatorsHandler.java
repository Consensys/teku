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

package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ACTIVE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_SIZE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_SIZE_DEFAULT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_TOKEN;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_TOKEN_DEFAULT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;
import static tech.pegasys.artemis.beaconrestapi.RestApiUtils.getParameterValueAsInt;
import static tech.pegasys.artemis.beaconrestapi.RestApiUtils.validateQueryParameter;

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
import tech.pegasys.artemis.beaconrestapi.schema.BeaconValidatorsResponse;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BeaconValidatorsHandler implements Handler {

  private final CombinedChainDataClient combinedClient;

  public BeaconValidatorsHandler(
      final CombinedChainDataClient combinedClient, final JsonProvider jsonProvider) {
    this.combinedClient = combinedClient;
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/beacon/validators";
  private final JsonProvider jsonProvider;

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Returns validators that match the specified query.",
      tags = {TAG_BEACON},
      description =
          "Returns validator information. If no parameters specified, the first page of current validators are returned.",
      queryParams = {
        @OpenApiParam(
            name = EPOCH,
            description = "Epoch to query. If not specified, current epoch is used."),
        @OpenApiParam(
            name = ACTIVE,
            description =
                "If specified, return only validators which are active in the specified epoch."),
        @OpenApiParam(
            name = PAGE_SIZE,
            description =
                "If specified, return only this many results. If not specified, defaults to "
                    + PAGE_SIZE_DEFAULT
                    + " results."),
        @OpenApiParam(
            name = PAGE_TOKEN,
            description =
                "If specified, return only this page of results. If not specified, defaults to page "
                    + PAGE_TOKEN_DEFAULT
                    + ".")
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = BeaconValidatorsResponse.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    final Map<String, List<String>> parameters = ctx.queryParamMap();
    SafeFuture<Optional<BeaconState>> future = null;
    final boolean activeOnly = parameters.containsKey(ACTIVE);
    int pageSize =
        getPositiveIntegerValueWithDefaultIfNotSupplied(parameters, PAGE_SIZE, PAGE_SIZE_DEFAULT);
    int pageToken =
        getPositiveIntegerValueWithDefaultIfNotSupplied(parameters, PAGE_TOKEN, PAGE_TOKEN_DEFAULT);

    Optional<Bytes32> optionalRoot = combinedClient.getBestBlockRoot();
    if (optionalRoot.isPresent()) {
      if (parameters.containsKey(EPOCH)) {
        future = queryByEpoch(validateQueryParameter(parameters, EPOCH), optionalRoot.get());
      } else {
        future = queryByRootHash(optionalRoot.get());
      }
      ctx.result(
          future.thenApplyChecked(
              state -> {
                if (state.isEmpty()) {
                  // empty list
                  return jsonProvider.objectToJSON(produceEmptyListResponse());
                } else {
                  return jsonProvider.objectToJSON(
                      produceResponse(state.get(), activeOnly, pageSize, pageToken));
                }
              }));
    } else {
      ctx.status(SC_NO_CONTENT);
    }
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

  private SafeFuture<Optional<BeaconState>> queryByRootHash(final Bytes32 root32) {
    return combinedClient.getStateByBlockRoot(root32);
  }

  private SafeFuture<Optional<BeaconState>> queryByEpoch(
      final String epochString, final Bytes32 blockRoot) {
    final UnsignedLong epoch = UnsignedLong.valueOf(epochString);
    return combinedClient.getStateAtSlot(
        BeaconStateUtil.compute_start_slot_at_epoch(epoch), blockRoot);
  }

  private final BeaconValidatorsResponse produceResponse(
      final BeaconState state, final boolean activeOnly, final int pageSize, final int pageToken) {
    return new BeaconValidatorsResponse(
        state.getValidators(),
        activeOnly,
        BeaconStateUtil.get_current_epoch(state),
        pageSize,
        pageToken);
  }

  private final BeaconValidatorsResponse produceEmptyListResponse() {
    return new BeaconValidatorsResponse(List.of());
  }
}
