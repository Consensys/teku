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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.Optional;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class StatusMessageFactory {

  private final RecentChainData recentChainData;

  public StatusMessageFactory(final RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  public Optional<StatusMessage> createStatusMessage() {
    if (recentChainData.isPreForkChoice()) {
      // We don't have chainhead information, so we can't generate an accurate status message
      return Optional.empty();
    }
    final ForkInfo forkInfo = recentChainData.getCurrentForkInfo().orElseThrow();
    final BeaconBlockAndState bestBlockAndState =
        recentChainData.getBestBlockAndState().orElseThrow();
    final Checkpoint finalizedCheckpoint = bestBlockAndState.getState().getFinalized_checkpoint();
    final BeaconBlock chainHead = bestBlockAndState.getBlock();

    return Optional.of(
        new StatusMessage(
            forkInfo.getForkDigest(),
            finalizedCheckpoint.getRoot(),
            finalizedCheckpoint.getEpoch(),
            chainHead.hash_tree_root(),
            chainHead.getSlot()));
  }
}
