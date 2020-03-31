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
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.storage.client.Store;

public class BlockImporter {
  private static final Logger LOG = LogManager.getLogger();
  private final RecentChainData storageClient;
  private final EventBus eventBus;
  private final StateTransition stateTransition = new StateTransition();

  public BlockImporter(RecentChainData storageClient, EventBus eventBus) {
    this.storageClient = storageClient;
    this.eventBus = eventBus;
  }

  @CheckReturnValue
  public BlockImportResult importBlock(SignedBeaconBlock block) {
    LOG.trace("Import block at slot {}: {}", block.getMessage().getSlot(), block);
    try {
      if (storageClient.containsBlock(block.getMessage().hash_tree_root())) {
        LOG.trace(
            "Importing known block {}.  Return successful result without re-processing.",
            block.getMessage().hash_tree_root());
        return BlockImportResult.knownBlock(block);
      }
      Store.Transaction transaction = storageClient.startStoreTransaction();
      final BlockImportResult result = on_block(transaction, block, stateTransition);
      if (!result.isSuccessful()) {
        LOG.trace(
            "Failed to import block for reason {}: {}",
            result.getFailureReason(),
            block.getMessage());
        return result;
      }
      LOG.trace("Successfully imported block {}", block.getMessage().hash_tree_root());

      final Optional<BlockProcessingRecord> record = result.getBlockProcessingRecord();
      transaction.commit().join();
      eventBus.post(new ImportedBlockEvent(block));
      record.ifPresent(eventBus::post);

      return result;
    } catch (Exception e) {
      LOG.error("Internal error while importing block: " + block.getMessage(), e);
      return BlockImportResult.internalError(e);
    }
  }
}
