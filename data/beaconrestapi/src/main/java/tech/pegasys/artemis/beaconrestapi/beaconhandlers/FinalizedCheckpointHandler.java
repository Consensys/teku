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

import java.util.HashMap;
import java.util.Map;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class FinalizedCheckpointHandler implements BeaconRestApiHandler {

  private final ChainStorageClient client;

  public FinalizedCheckpointHandler(ChainStorageClient client) {
    this.client = client;
  }

  @Override
  public String getPath() {
    return "/beacon/finalized_checkpoint";
  }

  @Override
  public Object handleRequest(RequestParams params) {
    Store store = client.getStore();
    if (store == null) {
      return null;
    }
    Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();
    Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put("epoch", finalizedCheckpoint.getEpoch().longValue());
    jsonObject.put("root", finalizedCheckpoint.getRoot().toHexString());
    return jsonObject;
  }
}
