/*
 * Copyright Consensys Software Inc., 2025
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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
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

    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().orElseThrow();
    final MinimalBeaconBlockSummary chainHead = recentChainData.getChainHead().orElseThrow();
    final Bytes4 forkDigest = recentChainData.getCurrentForkDigest().orElseThrow();

    return Optional.of(
        new StatusMessage(
            forkDigest,
            // Genesis finalized root is always ZERO because it's taken from the state and the
            // genesis block is calculated from the state so the state can't contain the actual
            // block root
            finalizedCheckpoint.getEpoch().isZero() ? Bytes32.ZERO : finalizedCheckpoint.getRoot(),
            finalizedCheckpoint.getEpoch(),
            chainHead.getRoot(),
            chainHead.getSlot()));
  }
}
