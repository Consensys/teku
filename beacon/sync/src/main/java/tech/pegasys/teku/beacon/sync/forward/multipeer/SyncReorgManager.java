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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import java.util.Optional;
import tech.pegasys.teku.beacon.sync.forward.multipeer.Sync.BlocksImportedSubscriber;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncReorgManager implements BlocksImportedSubscriber {
  static final int REORG_SLOT_THRESHOLD = 10;

  private final RecentChainData recentChainData;
  private final ForkChoiceTrigger forkChoiceTrigger;

  public SyncReorgManager(
      final RecentChainData recentChainData, final ForkChoiceTrigger forkChoiceTrigger) {
    this.recentChainData = recentChainData;
    this.forkChoiceTrigger = forkChoiceTrigger;
  }

  @Override
  public void onBlocksImported(final SignedBeaconBlock lastImportedBlock) {

    final Optional<ChainHead> currentHead = recentChainData.getChainHead();

    if (currentHead.isEmpty()) {
      return;
    }

    if (lastImportedBlock.getRoot().equals(currentHead.get().getRoot())) {
      return;
    }

    if (currentHead
        .get()
        .getSlot()
        .plus(REORG_SLOT_THRESHOLD)
        .isGreaterThan(lastImportedBlock.getSlot())) {
      return;
    }

    forkChoiceTrigger.reorgWhileSyncing(currentHead.get().getRoot(), lastImportedBlock.getRoot());
  }
}
