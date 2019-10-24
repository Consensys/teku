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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import io.libp2p.core.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksMessageRequest;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksMessageResponse;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.HelloMessage;
import tech.pegasys.artemis.util.alogger.ALogger;

public class RPCMethods {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final RPCMessageHandler<HelloMessage, HelloMessage> hello;
  private final RPCMessageHandler<GoodbyeMessage, Void> goodbye;
  private final RPCMessageHandler<BeaconBlocksMessageRequest, BeaconBlocksMessageResponse>
      beaconBlocks;

  public RPCMethods(BiFunction<Connection, HelloMessage, HelloMessage> helloHandler, BiFunction<Connection, GoodbyeMessage, Void> goodbyeHandler, BiFunction<Connection, BeaconBlocksMessageRequest, BeaconBlocksMessageResponse>
          beaconBlocksHandler) {

    this.hello =
        new RPCMessageHandler<>(
            "/eth2/beacon_chain/req/hello/1/ssz", HelloMessage.class, HelloMessage.class) {
          @Override
          protected CompletableFuture<HelloMessage> invokeLocal(
              Connection connection, HelloMessage msg) {
            return CompletableFuture.completedFuture(helloHandler.apply(connection, msg));
          }
        };

    this.goodbye =
        new RPCMessageHandler<GoodbyeMessage, Void>(
            "/eth2/beacon_chain/req/goodbye/1/ssz", GoodbyeMessage.class, Void.class) {
          @Override
          protected CompletableFuture<Void> invokeLocal(Connection connection, GoodbyeMessage msg) {
            return CompletableFuture.completedFuture(goodbyeHandler.apply(connection, msg));
          }
        }.setNotification();

    this.beaconBlocks =
        new RPCMessageHandler<BeaconBlocksMessageRequest, BeaconBlocksMessageResponse>(
            "/eth2/beacon_chain/req/beacon_blocks/1/ssz",
            BeaconBlocksMessageRequest.class,
            BeaconBlocksMessageResponse.class) {
          @Override
          protected CompletableFuture<BeaconBlocksMessageResponse> invokeLocal(
              Connection connection, BeaconBlocksMessageRequest msg) {
            return CompletableFuture.completedFuture(beaconBlocksHandler.apply(connection, msg));
          }
        };
  }

  public RPCMessageHandler<HelloMessage, HelloMessage> getHello() {
    return hello;
  }

  public RPCMessageHandler<GoodbyeMessage, Void> getGoodbye() {
    return goodbye;
  }

  public RPCMessageHandler<BeaconBlocksMessageRequest, BeaconBlocksMessageResponse>
      getBeaconBlocks() {
    return beaconBlocks;
  }

  public List<RPCMessageHandler<?, ?>> all() {
    return Arrays.asList(getHello(), getGoodbye(), getBeaconBlocks());
  }
}
