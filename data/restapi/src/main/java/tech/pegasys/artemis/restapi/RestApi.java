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

package tech.pegasys.artemis.restapi;

import static tech.pegasys.artemis.provider.JsonProvider.objectToJSON;

import com.google.common.primitives.UnsignedLong;
import io.javalin.Javalin;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class RestApi {

  private Javalin app;
  private ChainStorageClient chainStorageClient;
  private JvmLibP2PNetwork network;
  boolean isLibP2P = false;

  public RestApi(
      ChainStorageClient chainStorageClient, P2PNetwork p2pNetwork, final int portNumber) {
    this.chainStorageClient = chainStorageClient;
    if (p2pNetwork instanceof JvmLibP2PNetwork) {
      isLibP2P = true;
      network = (JvmLibP2PNetwork) p2pNetwork;
    }
    app = Javalin.create().start(portNumber);

    setGenesisTimeHandler();
    setBeaconHeadHandler();
    setBeaconBlockHandler();
    setBeaconStateHandler();
    setPeerIDHandler();
    setPeersHandler();
    setENRHandler();
  }

  private void setGenesisTimeHandler() {
    Map<String, Long> jsonObject = new HashMap<>();
    jsonObject.put("genesis_time", chainStorageClient.getGenesisTime().longValue());
    app.get("/node/genesis_time", ctx -> ctx.json(jsonObject));
  }

  private void setBeaconHeadHandler() {
    app.get(
        "/beacon/head",
        ctx -> {
          Bytes32 head_block_root = chainStorageClient.getBestBlockRoot();
          Bytes32 head_state_root = chainStorageClient.getBestBlockRootState().hash_tree_root();
          UnsignedLong head_block_slot = chainStorageClient.getBestSlot();
          Map<String, Object> jsonObject = new HashMap<>();
          jsonObject.put("slot", head_block_slot.longValue());
          jsonObject.put("block_root", head_block_root.toHexString());
          jsonObject.put("state_root", head_state_root.toHexString());
          ctx.json(jsonObject);
        });
  }

  // Skipping implementing block querying by slot for now
  private void setBeaconBlockHandler() {
    app.get(
        "/beacon/block",
        ctx -> {
          Bytes32 root = Bytes32.fromHexString(ctx.queryParam("root"));
          BeaconBlock block = chainStorageClient.getStore().getBlock(root);
          ctx.result(objectToJSON(block)).contentType("application/json");
        });
  }

  // Skipping implementing state querying by slot for now
  // NOTE: here the root is beacon block root, in parallel to how we index beacon state's in Store
  private void setBeaconStateHandler() {
    app.get(
        "/beacon/state",
        ctx -> {
          Bytes32 root = Bytes32.fromHexString(ctx.queryParam("root"));
          BeaconState state = chainStorageClient.getStore().getBlockState(root);
          ctx.result(objectToJSON(state)).contentType("application/json");
        });
  }

  private void setPeerIDHandler() {
    app.get(
        "/network/peer_id",
        ctx -> {
          if (isLibP2P) {
            ctx.result(network.getPeerIDString());
          } else {
            ctx.result("p2pNetwork not set to libP2P");
          }
        });
  }

  private void setPeersHandler() {
    app.get(
        "/network/peers",
        ctx -> {
          if (isLibP2P) {
            ctx.json(network.getPeersStrings().toArray());
          } else {
            ctx.result("p2pNetwork not set to libP2P");
          }
        });
  }

  private void setENRHandler() {
    app.get("/network/enr", ctx -> ctx.result("Discovery service not yet implemented"));
  }
}
