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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods;

import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.HelloMessage;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class HelloMessageFactory {

  private final ChainStorageClient chainStorageClient;

  public HelloMessageFactory(final ChainStorageClient chainStorageClient) {
    this.chainStorageClient = chainStorageClient;
  }

  public HelloMessage createHelloMessage() {
    return new HelloMessage(
        chainStorageClient.getBestBlockRootState().getFork().getCurrent_version(),
        chainStorageClient.getStore().getFinalizedCheckpoint().getRoot(),
        chainStorageClient.getStore().getFinalizedCheckpoint().getEpoch(),
        chainStorageClient.getBestBlockRoot(),
        chainStorageClient.getBestSlot());
  }
}
