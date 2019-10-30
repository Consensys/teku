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

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

public class StatusMessageFactory {

  private final ChainStorageClient chainStorageClient;

  public StatusMessageFactory(final ChainStorageClient chainStorageClient) {
    this.chainStorageClient = chainStorageClient;
  }

  public StatusMessage createStatusMessage() {
    final Bytes4 currentFork;
    final Bytes32 finalizedRoot;
    final UnsignedLong finalizedEpoch;
    if (chainStorageClient.getStore() != null) {
      currentFork = chainStorageClient.getBestBlockRootState().getFork().getCurrent_version();
      final Checkpoint finalizedCheckpoint = chainStorageClient.getStore().getFinalizedCheckpoint();
      finalizedRoot = finalizedCheckpoint.getRoot();
      finalizedEpoch = finalizedCheckpoint.getEpoch();
    } else {
      currentFork = Fork.VERSION_ZERO;
      finalizedRoot = Bytes32.ZERO;
      finalizedEpoch = UnsignedLong.ZERO;
    }
    return new StatusMessage(
        currentFork,
        finalizedRoot,
        finalizedEpoch,
        chainStorageClient.getBestBlockRoot(),
        chainStorageClient.getBestSlot());
  }
}
