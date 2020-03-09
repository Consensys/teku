/*
 * Copyright 2019 ConsenSys AG.
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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconChainHead;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.provider.JsonProvider;

public class BeaconChainHeadHandler implements Handler {

  private final ChainDataProvider chainDataProvider;
  private final JsonProvider jsonProvider;

  public BeaconChainHeadHandler(
      final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    this.chainDataProvider = chainDataProvider;
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/beacon/chainhead";

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get information about the canonical head from the beacon node.",
      tags = {TAG_BEACON},
      description =
          "Returns information about the head of the beacon chain from the node’s perspective.",
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = BeaconChainHead.class)),
        @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws JsonProcessingException {
    chainDataProvider
        .getBestBlockRoot()
        .ifPresentOrElse(
            block ->
                ctx.result(
                    chainDataProvider
                        .getStateByBlockRoot(block)
                        .thenApplyChecked(
                            optionalBeaconState -> {
                              if (optionalBeaconState.isPresent()) {
                                final BeaconState beaconState = optionalBeaconState.get();
                                final Checkpoint finalizedCheckpoint =
                                    beaconState.getFinalized_checkpoint();
                                final Checkpoint justifiedCheckpoint =
                                    beaconState.getCurrent_justified_checkpoint();
                                final Checkpoint previousJustifiedCheckpoint =
                                    beaconState.getPrevious_justified_checkpoint();

                                final BeaconChainHead chainHeadResponse =
                                    new BeaconChainHead(
                                        beaconState.getSlot(),
                                        compute_epoch_at_slot(beaconState.getSlot()),
                                        block,
                                        finalizedCheckpoint.getEpochSlot(),
                                        finalizedCheckpoint.getEpoch(),
                                        finalizedCheckpoint.getRoot(),
                                        justifiedCheckpoint.getEpochSlot(),
                                        justifiedCheckpoint.getEpoch(),
                                        justifiedCheckpoint.getRoot(),
                                        previousJustifiedCheckpoint.getEpochSlot(),
                                        previousJustifiedCheckpoint.getEpoch(),
                                        previousJustifiedCheckpoint.getRoot());

                                return jsonProvider.objectToJSON(chainHeadResponse);
                              }
                              ctx.status(SC_NO_CONTENT);
                              return null;
                            })),
            () -> ctx.status(SC_NO_CONTENT));
  }
}
