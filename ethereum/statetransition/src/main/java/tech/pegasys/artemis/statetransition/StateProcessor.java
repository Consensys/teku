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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.core.ForkChoiceUtil.get_head;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.core.results.BlockImportResult;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.client.RecentChainData;

/** Class to manage the state tree and initiate state transitions */
public class StateProcessor {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockImporter blockImporter;
  private final RecentChainData recentChainData;

  public StateProcessor(EventBus eventBus, RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
    this.blockImporter = new BlockImporter(recentChainData, eventBus);
    eventBus.register(this);
  }

  public Bytes32 processHead() {
    Store store = recentChainData.getStore();
    Bytes32 headBlockRoot = get_head(store);
    BeaconBlock headBlock = store.getBlock(headBlockRoot);
    recentChainData.updateBestBlock(headBlockRoot, headBlock.getSlot());
    return headBlockRoot;
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlockProposed(final ProposedBlockEvent blockProposedEvent) {
    LOG.trace("Preparing to import proposed block: {}", blockProposedEvent.getBlock());
    final BlockImportResult result = blockImporter.importBlock(blockProposedEvent.getBlock());
    if (result.isSuccessful()) {
      LOG.trace("Successfully imported proposed block: {}", blockProposedEvent.getBlock());
    } else {
      LOG.error(
          "Failed to import proposed block for reason + "
              + result.getFailureReason()
              + ": "
              + blockProposedEvent,
          result.getFailureCause().orElse(null));
    }
  }
}
