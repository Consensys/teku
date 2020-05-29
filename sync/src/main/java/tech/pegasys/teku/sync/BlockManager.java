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

package tech.pegasys.teku.sync;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.core.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class BlockManager extends Service implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final RecentChainData recentChainData;
  private final BlockImporter blockImporter;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;

  private final FutureItems<SignedBeaconBlock> futureBlocks;
  private final FetchRecentBlocksService recentBlockFetcher;
  private final Set<Bytes32> invalidBlockRoots =
      ConcurrentLimitedSet.create(500, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);

  BlockManager(
      final EventBus eventBus,
      final RecentChainData recentChainData,
      final BlockImporter blockImporter,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final FetchRecentBlocksService recentBlockFetcher) {
    this.eventBus = eventBus;
    this.recentChainData = recentChainData;
    this.blockImporter = blockImporter;
    this.pendingBlocks = pendingBlocks;
    this.futureBlocks = futureBlocks;
    this.recentBlockFetcher = recentBlockFetcher;
  }

  public static BlockManager create(
      final EventBus eventBus,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final FutureItems<SignedBeaconBlock> futureBlocks,
      final FetchRecentBlocksService recentBlockFetcher,
      final RecentChainData recentChainData,
      final BlockImporter blockImporter) {
    return new BlockManager(
        eventBus, recentChainData, blockImporter, pendingBlocks, futureBlocks, recentBlockFetcher);
  }

  @Override
  public SafeFuture<?> doStart() {
    this.eventBus.register(this);
    recentBlockFetcher.subscribeBlockFetched(this::importBlock);
    return recentBlockFetcher.start();
  }

  @Override
  protected SafeFuture<?> doStop() {
    eventBus.unregister(this);
    return recentBlockFetcher.stop();
  }

  @Subscribe
  @SuppressWarnings("unused")
  void onGossipedBlock(GossipedBlockEvent gossipedBlockEvent) {
    importBlock(gossipedBlockEvent.getBlock());
  }

  @Override
  public void onSlot(final UnsignedLong slot) {
    pendingBlocks.onSlot(slot);
    futureBlocks.prune(slot).forEach(this::importBlock);
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
        || recentChainData.getBlockByRoot(block.getMessage().hash_tree_root()).isPresent();
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
