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

package tech.pegasys.artemis.sync;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult.FailureReason;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.statetransition.events.BlockImportedEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.events.SlotEvent;

public class BlockPropagationManager extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final ChainStorageClient storageClient;
  private final BlockImporter blockImporter;
  private final PendingBlocks pendingBlocks;
  private final FutureBlocks futureBlocks;

  BlockPropagationManager(
      final EventBus eventBus,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter,
      final PendingBlocks pendingBlocks,
      final FutureBlocks futureBlocks) {
    this.eventBus = eventBus;
    this.storageClient = storageClient;
    this.blockImporter = blockImporter;
    this.pendingBlocks = pendingBlocks;
    this.futureBlocks = futureBlocks;
  }

  public static BlockPropagationManager create(
      final EventBus eventBus,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    final PendingBlocks pendingBlocks = PendingBlocks.create(eventBus);
    final FutureBlocks futureBlocks = new FutureBlocks();
    return new BlockPropagationManager(
        eventBus, storageClient, blockImporter, pendingBlocks, futureBlocks);
  }

  @Override
  public CompletableFuture<?> doStart() {
    this.eventBus.register(this);
    return this.pendingBlocks.start();
  }

  @Subscribe
  @SuppressWarnings("unused")
  void onGossipedBlock(GossipedBlockEvent gossipedBlockEvent) {
    final BeaconBlock block = gossipedBlockEvent.getBlock();
    if (blockIsKnown(block)) {
      // Nothing to do
      return;
    }

    importBlock(block, true, true);
  }

  @Subscribe
  @SuppressWarnings("unused")
  void onBlockImported(BlockImportedEvent blockImportedEvent) {
    // Check if any pending blocks can now be imported
    final BeaconBlock block = blockImportedEvent.getBlock();
    final Bytes32 blockRoot = block.signing_root("signature");
    pendingBlocks.remove(block);
    pendingBlocks
        .childrenOf(blockRoot)
        .forEach(
            child -> {
              pendingBlocks.remove(child);
              importBlock(child, false, true);
            });
  }

  @Subscribe
  void onSlot(final SlotEvent slotEvent) {
    futureBlocks.prune(slotEvent.getSlot()).forEach(block -> importBlock(block, false, false));
  }

  private boolean blockIsKnown(final BeaconBlock block) {
    return pendingBlocks.contains(block)
        || storageClient.getBlockByRoot(blockRoot(block)).isPresent();
  }

  private Bytes32 blockRoot(final BeaconBlock block) {
    return block.signing_root("signature");
  }

  private void importBlock(
      final BeaconBlock block, final boolean mayBePending, final boolean mayBeFromFuture) {
    final BlockImportResult result = blockImporter.importBlock(block);
    if (result.isSuccessful()) {
      LOG.trace("Imported gossiped block: {}", block);
    } else if (result.getFailureReason() == FailureReason.UNKNOWN_PARENT) {
      if (!mayBePending) {
        LOG.error("Encountered unexpected pending block", result.getFailureCause());
        return;
      }
      pendingBlocks.add(block);
    } else if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
      if (!mayBeFromFuture) {
        LOG.error("Encountered unexpected future block", result.getFailureCause());
        return;
      }
      futureBlocks.add(block);
    } else {
      LOG.trace(
          "Unable to import gossiped block for reason {}: {}", result.getFailureReason(), block);
    }
  }

  @Override
  protected CompletableFuture<?> doStop() {
    final CompletableFuture<?> shutdownFuture = pendingBlocks.stop();
    eventBus.unregister(this);
    return shutdownFuture;
  }
}
