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

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconHeadHandler implements BeaconRestApiHandler {

  private final ChainStorageClient client;

  public BeaconHeadHandler(ChainStorageClient client) {
    this.client = client;
  }

  @Override
  public String getPath() {
    return "/beacon/head";
  }

  @Override
  public Object handleRequest(RequestParams params) {
    Bytes32 head_block_root = client.getBestBlockRoot();
    if (head_block_root == null) {
      return null;
    }
    Bytes32 head_state_root = client.getBestBlockRootState().hash_tree_root();
    UnsignedLong head_block_slot = client.getBestSlot();
    Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put("slot", head_block_slot.longValue());
    jsonObject.put("block_root", head_block_root.toHexString());
    jsonObject.put("state_root", head_state_root.toHexString());
    return jsonObject;
  }
}
