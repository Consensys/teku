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
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_BEACON;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconValidatorsResponse;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
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
      summary = "Get validators from the running beacon node.",
      tags = {TAG_BEACON},
      description = "Requests validator information",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = BeaconValidatorsResponse.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws Exception {
    Optional<Bytes32> optionalRoot = combinedClient.getBestBlockRoot();
    if (optionalRoot.isPresent()) {
      SafeFuture<Optional<BeaconState>> future = queryByRootHash(optionalRoot.get());
      ctx.result(
          future.thenApplyChecked(
              state -> {
                if (state.isEmpty()) {
                  // empty list
                  return jsonProvider.objectToJSON(
                      new BeaconValidatorsResponse(new SSZList<>(Validator.class, 0L)));
                }
                return jsonProvider.objectToJSON(
                    new BeaconValidatorsResponse(state.get().getValidators()));
              }));
    } else {
      ctx.status(SC_NO_CONTENT);
    }
  }

  private SafeFuture<Optional<BeaconState>> queryByRootHash(final Bytes32 root32) {
    return combinedClient.getStateByBlockRoot(root32);
  }
}
