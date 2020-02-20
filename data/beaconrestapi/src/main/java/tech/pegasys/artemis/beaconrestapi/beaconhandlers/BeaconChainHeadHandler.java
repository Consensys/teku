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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconChainHeadResponse;
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
      tags = {"Beacon"},
      description = "Requests the canonical head from the beacon node.",
      responses = {
        @OpenApiResponse(
            status = "200",
            content = @OpenApiContent(from = BeaconChainHeadResponse.class)),
        @OpenApiResponse(
            status = "204",
            description = "No Content will be returned if any of the block roots are null")
      })
  @Override
  public void handle(Context ctx) throws JsonProcessingException {
    Bytes32 head_block_root = client.getBestBlockRoot();

    UnsignedLong head_block_slot = client.getBestSlot();
    UnsignedLong finalized_epoch = client.getFinalizedEpoch();
    Bytes32 finalized_root = client.getFinalizedRoot();
    UnsignedLong justified_epoch = client.getJustifiedEpoch();
    Bytes32 justified_root = client.getJustifiedRoot();

    if (head_block_root == null || finalized_root == null || justified_root == null) {
      ctx.status(SC_NO_CONTENT);
      return;
    }

    BeaconChainHeadResponse chainHeadResponse =
        new BeaconChainHeadResponse(
            head_block_slot,
            compute_epoch_at_slot(head_block_slot),
            head_block_root,
            compute_start_slot_at_epoch(finalized_epoch),
            finalized_epoch,
            finalized_root,
            compute_start_slot_at_epoch(justified_epoch),
            justified_epoch,
            justified_root);

    ctx.result(jsonProvider.objectToJSON(chainHeadResponse));
  }
}
