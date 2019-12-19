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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.BlockImporter;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.events.BlockImportedEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.async.GoodFuture;

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
  public GoodFuture<?> doStart() {
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

    if (!blockImporter.isBlockAttached(block)) {
      pendingBlocks.add(block);
    } else if (blockImporter.isFutureBlock(block)) {
      futureBlocks.add(block);
    } else {
      importBlock(block);
    }
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
              if (blockImporter.isFutureBlock(child)) {
                futureBlocks.add(child);
              } else {
                importBlock(child);
              }
            });
  }

  @Subscribe
  void onSlot(final SlotEvent slotEvent) {
    futureBlocks.prune(slotEvent.getSlot()).forEach(this::importBlock);
  }

  private boolean blockIsKnown(final BeaconBlock block) {
    return pendingBlocks.contains(block)
        || storageClient.getBlockByRoot(blockRoot(block)).isPresent();
  }

  private Bytes32 blockRoot(final BeaconBlock block) {
    return block.signing_root("signature");
  }

  private void importBlock(final BeaconBlock block) {
    try {
      blockImporter.importBlock(block);
    } catch (StateTransitionException e) {
      LOG.debug("Unable to import propagated block " + block, e);
    }
  }

  @Override
  protected GoodFuture<?> doStop() {
    final GoodFuture<?> shutdownFuture = pendingBlocks.stop();
    eventBus.unregister(this);
    return shutdownFuture;
  }
}
