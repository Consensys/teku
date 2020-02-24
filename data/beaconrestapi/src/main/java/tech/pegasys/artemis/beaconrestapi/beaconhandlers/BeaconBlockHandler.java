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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.schema.BeaconBlockResponse;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class BeaconBlockHandler implements Handler {

  private final ChainStorageClient client;
  private final HistoricalChainData historicalChainData;
  public static final String ROUTE = "/beacon/block";
  private final JsonProvider jsonProvider;

  public BeaconBlockHandler(
      final ChainStorageClient client,
      final HistoricalChainData historicalChainData,
      final JsonProvider jsonProvider) {
    this.client = client;
    this.historicalChainData = historicalChainData;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public void handle(final Context ctx) throws Exception {
    final Map<String, List<String>> queryParamMap = ctx.queryParamMap();
    if (queryParamMap.containsKey(ROOT)) {
      final Bytes32 root = Bytes32.fromHexString(queryParamMap.get(ROOT).get(0));
      if (client.getStore() != null) {
        ctx.result(
            jsonProvider.objectToJSON(new BeaconBlockResponse(client.getStore().getBlock(root))));
        return;
      } else {
        ctx.status(SC_NOT_FOUND);
        return;
      }
    }

    final UnsignedLong slot;
    if (queryParamMap.containsKey(EPOCH)) {
      slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(queryParamMap.get(EPOCH).get(0)));
    } else if (queryParamMap.containsKey(SLOT)) {
      slot = UnsignedLong.valueOf(queryParamMap.get(SLOT).get(0));
    } else {
      ctx.status(SC_NOT_FOUND);
      return;
    }

    final Optional<BeaconBlock> blockBySlot = getBlockBySlot(slot);
    if (blockBySlot.isPresent()) {
      ctx.result(jsonProvider.objectToJSON(new BeaconBlockResponse(blockBySlot.get())));
    } else {
      ctx.status(SC_NOT_FOUND);
    }
  }

  private Optional<BeaconBlock> getBlockBySlot(final UnsignedLong slot) {
    return client
        .getBlockRootBySlot(slot)
        .map(root -> client.getStore().getBlock(root))
        .or(
            () -> {
              final Optional<SignedBeaconBlock> signedBeaconBlock =
                  historicalChainData.getFinalizedBlockAtSlot(slot).join();
              return signedBeaconBlock.map(SignedBeaconBlock::getMessage);
            });
  }
}
