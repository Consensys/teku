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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconChainHeadResponse;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconChainHeadHandler implements Handler {

  private final ChainStorageClient client;
  private final JsonProvider jsonProvider;

  public BeaconChainHeadHandler(ChainStorageClient client, JsonProvider jsonProvider) {
    this.client = client;
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/beacon/chainhead";

  // TODO: make sure finalized and justified root methods return null if
  // we don't have them in store yet. So that we can handle them better instead of
  // returning zero.
  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get the canonical head from the beacon node.",
      tags = {TAG_BEACON},
      description = "Requests the canonical head from the beacon node.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = BeaconChainHeadResponse.class)),
        @OpenApiResponse(
            status = RES_NO_CONTENT,
            description = "No Content will be returned if pre Genesis state"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(Context ctx) throws JsonProcessingException {
    Bytes32 head_block_root = client.getBestBlockRoot();
    if (head_block_root == null) {
      ctx.status(SC_NO_CONTENT);
      return;
    }

    // derive all other state from the head_block_root
    BeaconState beaconState = client.getStore().getBlockState(head_block_root);
    Checkpoint finalizedCheckpoint = beaconState.getFinalized_checkpoint();
    Checkpoint justifiedCheckpoint = beaconState.getCurrent_justified_checkpoint();
    Checkpoint previousJustifiedCheckpoint = beaconState.getPrevious_justified_checkpoint();

    BeaconChainHeadResponse chainHeadResponse =
        new BeaconChainHeadResponse(
            beaconState.getSlot(),
            compute_epoch_at_slot(beaconState.getSlot()),
            head_block_root,
            finalizedCheckpoint.getEpochSlot(),
            finalizedCheckpoint.getEpoch(),
            finalizedCheckpoint.getRoot(),
            justifiedCheckpoint.getEpochSlot(),
            justifiedCheckpoint.getEpoch(),
            justifiedCheckpoint.getRoot(),
            previousJustifiedCheckpoint.getEpochSlot(),
            previousJustifiedCheckpoint.getEpoch(),
            previousJustifiedCheckpoint.getRoot());

    ctx.result(jsonProvider.objectToJSON(chainHeadResponse));
  }
}
