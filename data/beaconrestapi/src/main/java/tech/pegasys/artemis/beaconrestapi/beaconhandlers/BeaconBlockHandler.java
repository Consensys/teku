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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class BeaconBlockHandler implements BeaconRestApiHandler {

  private final ChainStorageClient client;
  private final HistoricalChainData historicalChainData;

  public BeaconBlockHandler(ChainStorageClient client, EventBus eventBus) {
    this.client = client;
    this.historicalChainData = new HistoricalChainData(eventBus);
  }

  @Override
  public String getPath() {
    return "/beacon/block";
  }

  @Override
  public Object handleRequest(RequestParams param) {
    Map<String, List<String>> queryParamMap = param.getQueryParamMap();
    if (queryParamMap.containsKey("root")) {
      Bytes32 root = Bytes32.fromHexString(param.getQueryParam("root"));
      return client.getStore() != null ? client.getStore().getBlock(root) : null;
    }

    UnsignedLong slot;
    if (queryParamMap.containsKey("epoch")) {
      slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(param.getQueryParam("epoch")));
    } else if (queryParamMap.containsKey("slot")) {
      slot = UnsignedLong.valueOf(param.getQueryParam("slot"));
    } else {
      return null;
    }

    Optional<Pair<BeaconBlock, Bytes32>> blockAndRoot = getBlockBySlot(slot);
    if (blockAndRoot.isEmpty()) {
      return null;
    }

    Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put("blockRoot", blockAndRoot.get().getRight().toHexString());
    jsonObject.put("block", blockAndRoot.get().getLeft());

    return jsonObject;
  }

  private Optional<Pair<BeaconBlock, Bytes32>> getBlockBySlot(UnsignedLong slot) {
    Optional<Bytes32> blockRootAtSlot = client.getBlockRootBySlot(slot);
    return blockRootAtSlot
        .flatMap(root -> Optional.of(Pair.of(client.getStore().getBlock(root), root)))
        .or(
            () -> {
              Optional<SignedBeaconBlock> signedBeaconBlock =
                  historicalChainData.getFinalizedBlockAtSlot(slot).join();
              if (signedBeaconBlock.isPresent()) {
                Bytes32 historicalBlockRoot = signedBeaconBlock.get().getMessage().hash_tree_root();
                return Optional.of(
                    Pair.of(signedBeaconBlock.get().getMessage(), historicalBlockRoot));
              } else {
                return Optional.empty();
              }
            });
  };
}
