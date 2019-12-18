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

import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.statetransition.events.BlockImportedEvent;
import tech.pegasys.artemis.statetransition.util.ForkChoiceUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class BlockImporter {
  private static final Logger LOG = LogManager.getLogger();
  private final ChainStorageClient storageClient;
  private final EventBus eventBus;
  private final StateTransition stateTransition = new StateTransition(false);

  public BlockImporter(ChainStorageClient storageClient, EventBus eventBus) {
    this.storageClient = storageClient;
    this.eventBus = eventBus;
  }

  /**
   * @param block The block to check
   * @return {@code true} If this block is from the future according to our node's clock
   */
  public boolean isFutureBlock(BeaconBlock block) {
    if (storageClient.isPreGenesis()) {
      return true;
    }
    final UnsignedLong genesisTime = storageClient.getStore().getGenesisTime();
    return ForkChoiceUtil.isFutureBlock(block, genesisTime, storageClient.getStore().getTime());
  }

  /**
   * @param block The block to check.
   * @return {@code true} If block is attached to the local chain
   */
  public boolean isBlockAttached(BeaconBlock block) {
    return storageClient.getBlockState(block.getParent_root()).isPresent();
  }

  public void importBlock(BeaconBlock block) throws StateTransitionException {
    LOG.trace("Import block at slot {}: {}", block.getSlot(), block);
    Store.Transaction transaction = storageClient.startStoreTransaction();
    final BlockProcessingRecord record = on_block(transaction, block, stateTransition);
    transaction.commit().join();
    eventBus.post(new BlockImportedEvent(block));
    eventBus.post(record);
  }
}
