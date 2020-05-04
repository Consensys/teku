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

package tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class StatusMessageFactory {

  private final RecentChainData recentChainData;

  public StatusMessageFactory(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  public StatusMessage createStatusMessage() {
    if (recentChainData.isPreGenesis()) {
      return StatusMessage.createPreGenesisStatus();
    }

    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    final Bytes32 finalizedRoot = finalizedCheckpoint.getRoot();
    final UnsignedLong finalizedEpoch = finalizedCheckpoint.getEpoch();

    return new StatusMessage(
        recentChainData.getCurrentForkInfo().orElseThrow().getForkDigest(),
        finalizedRoot,
        finalizedEpoch,
        recentChainData.getBestBlockRoot().orElse(Bytes32.ZERO),
        recentChainData.getBestSlot());
  }
}
