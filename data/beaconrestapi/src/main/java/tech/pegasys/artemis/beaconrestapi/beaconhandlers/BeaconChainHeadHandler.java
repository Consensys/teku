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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconChainHeadHandler implements BeaconRestApiHandler {

  private final ChainStorageClient client;

  public BeaconChainHeadHandler(ChainStorageClient client) {
    this.client = client;
  }

  @Override
  public String getPath() {
    return "/beacon/chainhead";
  }

  // TODO: make sure finalized and justified root methods return null if
  // we don't have them in store yet. So that we can handle them better instead of
  // returning zero.
  @Override
  public Object handleRequest(RequestParams params) {
    Bytes32 head_block_root = client.getBestBlockRoot();

    UnsignedLong head_block_slot = client.getBestSlot();
    UnsignedLong finalized_epoch = client.getFinalizedEpoch();
    Bytes32 finalized_root = client.getFinalizedRoot();
    UnsignedLong justified_epoch = client.getJustifiedEpoch();
    Bytes32 justified_root = client.getJustifiedRoot();

    if (head_block_root == null || finalized_root == null || justified_root == null) {
      return null;
    }

    Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put("headSlot", head_block_slot.longValue());
    jsonObject.put("headEpoch", compute_epoch_at_slot(head_block_slot).longValue());
    jsonObject.put("headBlockRoot", head_block_root.toHexString());

    jsonObject.put("finalizedSlot", compute_start_slot_at_epoch(finalized_epoch).longValue());
    jsonObject.put("finalizedEpoch", finalized_epoch.longValue());
    jsonObject.put("finalizedBlockRoot", finalized_root.toHexString());

    jsonObject.put("justifiedSlot", compute_start_slot_at_epoch(justified_epoch).longValue());
    jsonObject.put("justifiedEpoch", justified_epoch.longValue());
    jsonObject.put("justifiedBlockRoot", justified_root.toHexString());
    return jsonObject;
  }
}
