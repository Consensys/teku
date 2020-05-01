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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.core.results.BlockImportResult;
import tech.pegasys.artemis.data.BlockProcessingRecord;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.artemis.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.artemis.statetransition.forkchoice.ForkChoice;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BlockImporter {
  private static final Logger LOG = LogManager.getLogger();
  private final RecentChainData recentChainData;
  private final ForkChoice forkChoice;
  private final EventBus eventBus;

  public BlockImporter(
      final RecentChainData recentChainData, final ForkChoice forkChoice, final EventBus eventBus) {
    this.recentChainData = recentChainData;
    this.forkChoice = forkChoice;
    this.eventBus = eventBus;
    eventBus.register(this);
  }

  @CheckReturnValue
  public BlockImportResult importBlock(SignedBeaconBlock block) {
    LOG.trace("Import block at slot {}: {}", block.getMessage().getSlot(), block);
    try {
      if (recentChainData.containsBlock(block.getMessage().hash_tree_root())) {
        LOG.trace(
            "Importing known block {}.  Return successful result without re-processing.",
            block.getMessage().hash_tree_root());
        return BlockImportResult.knownBlock(block);
      }

      BlockImportResult result = forkChoice.onBlock(block);
      final Optional<BlockProcessingRecord> record = result.getBlockProcessingRecord();
      eventBus.post(new ImportedBlockEvent(block));
      record.ifPresent(eventBus::post);
      return result;
    } catch (Exception e) {
      LOG.error("Internal error while importing block: " + block.getMessage(), e);
      return BlockImportResult.internalError(e);
    }
  }

  public SafeFuture<BlockImportResult> importBlockAsync(final SignedBeaconBlock block) {
    return SafeFuture.of(() -> importBlock(block));
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlockProposed(final ProposedBlockEvent blockProposedEvent) {
    LOG.trace("Preparing to import proposed block: {}", blockProposedEvent.getBlock());
    final BlockImportResult result = importBlock(blockProposedEvent.getBlock());
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
