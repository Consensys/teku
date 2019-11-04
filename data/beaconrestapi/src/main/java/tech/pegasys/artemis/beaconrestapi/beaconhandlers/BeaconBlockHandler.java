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

import static tech.pegasys.artemis.provider.JsonProvider.objectToJSON;

import io.javalin.Javalin;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.beaconrestapi.handlerinterfaces.BeaconHandlerInterface;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconBlockHandler implements BeaconHandlerInterface {

  private Javalin app;
  private ChainStorageClient client;

  @Override
  public BeaconBlockHandler init(Javalin app, ChainStorageClient client) {
    this.app = app;
    this.client = client;
    return this;
  }

  @Override
  public void run() {
    app.get(
        "/beacon/block",
        ctx -> {
          Bytes32 root = Bytes32.fromHexString(ctx.queryParam("root"));
          BeaconBlock block = client.getStore().getBlock(root);
          ctx.result(objectToJSON(block)).contentType("application/json");
        });
  }
}
