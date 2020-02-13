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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult.FailureReason;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.collections.LimitedSet;
import tech.pegasys.artemis.util.collections.LimitedSet.Mode;

public class BlockPropagationManager extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final ChainStorageClient storageClient;
  private final BlockImporter blockImporter;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;
  private final FutureItems<SignedBeaconBlock> futureBlocks;
  private final FetchRecentBlocksService recentBlockFetcher;
  private final Set<Bytes32> invalidBlockRoots =
      LimitedSet.create(500, Mode.DROP_LEAST_RECENTLY_ACCESSED);

  BlockPropagationManager(
      final EventBus eventBus,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final FetchRecentBlocksService recentBlockFetcher) {
    this.eventBus = eventBus;
    this.storageClient = storageClient;
    this.blockImporter = blockImporter;
    this.pendingBlocks = pendingBlocks;
    this.futureBlocks = futureBlocks;
    this.recentBlockFetcher = recentBlockFetcher;
  }

  public static BlockPropagationManager create(
      final EventBus eventBus,
      final Eth2Network eth2Network,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    final PendingPool<SignedBeaconBlock> pendingBlocks = PendingPool.createForBlocks(eventBus);
    final FutureItems<SignedBeaconBlock> futureBlocks =
        new FutureItems<>(SignedBeaconBlock::getSlot);
    final FetchRecentBlocksService recentBlockFetcher =
        FetchRecentBlocksService.create(eth2Network, pendingBlocks);
    return new BlockPropagationManager(
        eventBus, storageClient, blockImporter, pendingBlocks, futureBlocks, recentBlockFetcher);
  }

  @Override
  public SafeFuture<?> doStart() {
    this.eventBus.register(this);
    recentBlockFetcher.subscribeBlockFetched(this::importBlock);
    return SafeFuture.allOf(recentBlockFetcher.start(), pendingBlocks.start());
  }

  @Override
  protected SafeFuture<?> doStop() {
    eventBus.unregister(this);
    return SafeFuture.allOf(recentBlockFetcher.stop(), pendingBlocks.stop());
  }

  @Subscribe
  @SuppressWarnings("unused")
  void onGossipedBlock(GossipedBlockEvent gossipedBlockEvent) {
    importBlock(gossipedBlockEvent.getBlock());
  }

  @Subscribe
  void onSlot(final SlotEvent slotEvent) {
    futureBlocks.prune(slotEvent.getSlot()).forEach(this::importBlock);
  }

  @Subscribe
  @SuppressWarnings("unused")
  void onBlockImported(ImportedBlockEvent blockImportedEvent) {
    // Check if any pending blocks can now be imported
    final SignedBeaconBlock block = blockImportedEvent.getBlock();
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    pendingBlocks.remove(block);
    final List<SignedBeaconBlock> children = pendingBlocks.getItemsDependingOn(blockRoot, false);
    children.forEach(pendingBlocks::remove);
    children.forEach(this::importBlock);
  }

  private void importBlock(final SignedBeaconBlock block) {
    recentBlockFetcher.cancelRecentBlockRequest(block.getMessage().hash_tree_root());
    if (!shouldImportBlock(block)) {
      return;
    }

    final BlockImportResult result = blockImporter.importBlock(block);
    if (result.isSuccessful()) {
      LOG.trace("Imported block: {}", block);
    } else if (result.getFailureReason() == FailureReason.UNKNOWN_PARENT) {
      pendingBlocks.add(block);
    } else if (result.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
      futureBlocks.add(block);
    } else {
      LOG.trace("Unable to import block for reason {}: {}", result.getFailureReason(), block);
      dropInvalidBlock(block);
    }
  }

  private boolean shouldImportBlock(final SignedBeaconBlock block) {
    if (blockIsKnown(block)) {
      return false;
    }
    if (blockIsInvalid(block)) {
      dropInvalidBlock(block);
      return false;
    }
    return true;
  }

  private boolean blockIsKnown(final SignedBeaconBlock block) {
    return pendingBlocks.contains(block)
        || futureBlocks.contains(block)
        || storageClient.getBlockByRoot(block.getMessage().hash_tree_root()).isPresent();
  }

  private boolean blockIsInvalid(final SignedBeaconBlock block) {
    return invalidBlockRoots.contains(block.getMessage().hash_tree_root())
        || invalidBlockRoots.contains(block.getParent_root());
  }

  private void dropInvalidBlock(final SignedBeaconBlock block) {
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    final Set<SignedBeaconBlock> blocksToDrop = new HashSet<>();
    blocksToDrop.add(block);
    blocksToDrop.addAll(pendingBlocks.getItemsDependingOn(blockRoot, true));

    blocksToDrop.forEach(
        blockToDrop -> {
          invalidBlockRoots.add(blockToDrop.getMessage().hash_tree_root());
          pendingBlocks.remove(blockToDrop);
        });
  }
}
