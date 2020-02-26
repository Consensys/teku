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

import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ROOT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.HistoricalChainData;

public class BeaconBlockHandler implements BeaconRestApiHandler {

  private final ChainStorageClient client;
  private final HistoricalChainData historicalChainData;

  public BeaconBlockHandler(ChainStorageClient client, HistoricalChainData historicalChainData) {
    this.client = client;
    this.historicalChainData = historicalChainData;
  }

  @Override
  public String getPath() {
    return "/beacon/block";
  }

  @Override
  public Object handleRequest(RequestParams param) {
    Map<String, List<String>> queryParamMap = param.getQueryParamMap();
    if (queryParamMap.containsKey(ROOT)) {
      Bytes32 root = Bytes32.fromHexString(param.getQueryParam(ROOT));
      return client.getStore() != null ? client.getStore().getBlock(root) : null;
    }

    UnsignedLong slot;
    if (queryParamMap.containsKey(EPOCH)) {
      slot = compute_start_slot_at_epoch(UnsignedLong.valueOf(param.getQueryParam(EPOCH)));
    } else if (queryParamMap.containsKey(SLOT)) {
      slot = UnsignedLong.valueOf(param.getQueryParam(SLOT));
    } else {
      return null;
    }

    return getBlockBySlot(slot)
        .map(
            block ->
                ImmutableMap.of("block", block, "blockRoot", block.hash_tree_root().toHexString()))
        .orElse(null);
  }

  private Optional<BeaconBlock> getBlockBySlot(UnsignedLong slot) {
    return client
        .getBlockRootBySlot(slot)
        .map(root -> client.getStore().getBlock(root))
        .or(
            () -> {
              Optional<SignedBeaconBlock> signedBeaconBlock =
                  historicalChainData.getFinalizedBlockAtSlot(slot).join();
              return signedBeaconBlock.map(SignedBeaconBlock::getMessage);
            });
  };
}
