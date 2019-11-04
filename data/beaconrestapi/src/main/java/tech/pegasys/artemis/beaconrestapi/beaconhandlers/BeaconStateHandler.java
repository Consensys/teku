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
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class BeaconStateHandler implements BeaconHandlerInterface {

  private Javalin app;
  private ChainStorageClient client;

  @Override
  public BeaconStateHandler init(Javalin app, ChainStorageClient client) {
    this.app = app;
    this.client = client;
    return this;
  }

  @Override
  public void run() {
    app.get(
        "/beacon/state",
        ctx -> {
          Bytes32 root = Bytes32.fromHexString(ctx.queryParam("root"));
          BeaconState state = client.getStore().getBlockState(root);
          ctx.result(objectToJSON(state)).contentType("application/json");
        });
  }
}
