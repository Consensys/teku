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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconRestApiHandler;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconBlockHandler implements BeaconRestApiHandler {

  private final ChainStorageClient client;

  public BeaconBlockHandler(ChainStorageClient client) {
    this.client = client;
  }

  @Override
  public String getPath() {
    return "/beacon/block";
  }

  @Override
  public String handleRequest(RequestParams param) {
    Bytes32 root = Bytes32.fromHexString(param.getQueryParam("root"));
    BeaconBlock block = client.getStore().getBlock(root);
    return JsonProvider.objectToJSON(block);
  }
}
