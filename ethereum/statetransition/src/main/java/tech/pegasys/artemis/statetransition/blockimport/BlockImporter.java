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

package tech.pegasys.artemis.statetransition.blockimport;

import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_block;

import com.google.common.eventbus.EventBus;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.events.BlockImportedEvent;
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

  @CheckReturnValue
  public BlockImportResult importBlock(SignedBeaconBlock block) {
    LOG.trace("Import block at slot {}: {}", block.getMessage().getSlot(), block);
    try {
      Store.Transaction transaction = storageClient.startStoreTransaction();
      final BlockImportResult result = on_block(transaction, block, stateTransition);
      if (!result.isSuccessful()) {
        return result;
      }

      final BlockProcessingRecord record = result.getBlockProcessingRecord();
      transaction.commit().join();
      eventBus.post(new BlockImportedEvent(block));
      eventBus.post(record);

      return result;
    } catch (Exception e) {
      LOG.error("Internal error while importing block " + block.getMessage(), e);
      return BlockImportResult.internalError(e);
    }
  }
}
