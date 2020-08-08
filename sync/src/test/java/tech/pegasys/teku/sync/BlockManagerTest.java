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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.ImportedBlocks;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class BlockManagerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(2);
  private final EventBus localEventBus = new EventBus();
  private final EventBus remoteEventBus = new EventBus();
  private final UInt64 historicalBlockTolerance = UInt64.valueOf(5);
  private final UInt64 futureBlockTolerance = UInt64.valueOf(2);
  private final PendingPool<SignedBeaconBlock> pendingBlocks =
      PendingPool.createForBlocks(historicalBlockTolerance, futureBlockTolerance);
  private final FutureItems<SignedBeaconBlock> futureBlocks =
      new FutureItems<>(SignedBeaconBlock::getSlot);
  private final FetchRecentBlocksService recentBlockFetcher = mock(FetchRecentBlocksService.class);

  private final RecentChainData localRecentChainData =
      MemoryOnlyRecentChainData.create(localEventBus);
  private final RecentChainData remoteRecentChainData =
      MemoryOnlyRecentChainData.create(remoteEventBus);
  private final BeaconChainUtil localChain =
      BeaconChainUtil.create(localRecentChainData, validatorKeys);
  private final BeaconChainUtil remoteChain =
      BeaconChainUtil.create(remoteRecentChainData, validatorKeys);
  private final ForkChoice forkChoice = new ForkChoice(localRecentChainData, new StateTransition());
  private final ImportedBlocks importedBlocks = new ImportedBlocks(localEventBus);

  private final BlockImporter blockImporter =
      new BlockImporter(localRecentChainData, forkChoice, localEventBus);
  private final BlockManager blockManager =
      new BlockManager(
          localEventBus,
          localRecentChainData,
          blockImporter,
          pendingBlocks,
          futureBlocks,
          recentBlockFetcher);

  private final UInt64 genesisSlot = UInt64.valueOf(Constants.GENESIS_SLOT);
  private UInt64 currentSlot = genesisSlot;

  @BeforeEach
  public void setup() {
    localChain.initializeStorage();
    remoteChain.initializeStorage();
    when(recentBlockFetcher.start()).thenReturn(SafeFuture.completedFuture(null));
    when(recentBlockFetcher.stop()).thenReturn(SafeFuture.completedFuture(null));
    assertThat(blockManager.start()).isCompleted();
  }

  @AfterEach
  public void cleanup() throws Exception {
    assertThat(blockManager.stop()).isCompleted();
    importedBlocks.close();
  }

  @Test
  public void onGossipedBlock_shouldImport() throws Exception {
    final UInt64 nextSlot = genesisSlot.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock = localChain.createBlockAtSlot(nextSlot);
    incrementSlot();

    assertThat(importedBlocks.get()).isEmpty();
    localEventBus.post(new GossipedBlockEvent(nextBlock));
    assertThat(importedBlocks.get()).containsExactly(nextBlock);
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onGossipedBlock_unattachedBlock() throws Exception {
    final UInt64 nextSlot = genesisSlot.plus(UInt64.ONE);
    final UInt64 nextNextSlot = nextSlot.plus(UInt64.ONE);
    // Create 2 blocks
    remoteChain.createAndImportBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock = remoteChain.createAndImportBlockAtSlot(nextNextSlot);

    incrementSlot();
    incrementSlot();
    localEventBus.post(new GossipedBlockEvent(nextNextBlock));
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.contains(nextNextBlock)).isTrue();
  }

  @Test
  public void onGossipedBlock_futureBlock() throws Exception {
    final UInt64 nextSlot = genesisSlot.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock = remoteChain.createAndImportBlockAtSlot(nextSlot);

    localEventBus.post(new GossipedBlockEvent(nextBlock));
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(nextBlock)).isTrue();
  }

  @Test
  public void onGossipedBlock_unattachedFutureBlock() throws Exception {
    final UInt64 nextSlot = genesisSlot.plus(UInt64.ONE);
    final UInt64 nextNextSlot = nextSlot.plus(UInt64.ONE);
    // Create 2 blocks
    remoteChain.createAndImportBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock = remoteChain.createAndImportBlockAtSlot(nextNextSlot);

    incrementSlot();
    localEventBus.post(new GossipedBlockEvent(nextNextBlock));
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.contains(nextNextBlock)).isTrue();
  }

  @Test
  public void onBlockImported_withPendingBlocks() throws Exception {
    final int blockCount = 3;
    final List<SignedBeaconBlock> blocks = new ArrayList<>(blockCount);

    for (int i = 0; i < blockCount; i++) {
      final UInt64 nextSlot = incrementSlot();
      blocks.add(remoteChain.createAndImportBlockAtSlot(nextSlot));
    }

    // Gossip all blocks except the first
    blocks.subList(1, blockCount).stream()
        .map(GossipedBlockEvent::new)
        .forEach(localEventBus::post);
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import next block, causing remaining blocks to be imported
    assertThat(blockImporter.importBlock(blocks.get(0)).get().isSuccessful()).isTrue();
    assertThat(importedBlocks.get()).containsExactlyElementsOf(blocks);
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImportFailure_withPendingDependantBlocks() throws Exception {
    final int invalidChainDepth = 3;
    final List<SignedBeaconBlock> invalidBlockDescendants = new ArrayList<>(invalidChainDepth);

    final SignedBeaconBlock invalidBlock =
        remoteChain.createBlockAtSlotFromInvalidProposer(incrementSlot());
    Bytes32 parentBlockRoot = invalidBlock.getMessage().hash_tree_root();
    for (int i = 0; i < invalidChainDepth; i++) {
      final UInt64 nextSlot = incrementSlot();
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(nextSlot.longValue(), parentBlockRoot);
      invalidBlockDescendants.add(block);
      parentBlockRoot = block.getMessage().hash_tree_root();
    }

    // Gossip all blocks except the first
    invalidBlockDescendants.stream().map(GossipedBlockEvent::new).forEach(localEventBus::post);
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth);

    // Gossip next block, causing dependent blocks to be dropped when the import fails
    localEventBus.post(new GossipedBlockEvent(invalidBlock));
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // If any invalid block is again gossiped, it should be ignored
    invalidBlockDescendants.stream().map(GossipedBlockEvent::new).forEach(localEventBus::post);
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImportFailure_withUnconnectedPendingDependantBlocks() throws Exception {
    final int invalidChainDepth = 3;
    final List<SignedBeaconBlock> invalidBlockDescendants = new ArrayList<>(invalidChainDepth);

    final SignedBeaconBlock invalidBlock =
        remoteChain.createBlockAtSlotFromInvalidProposer(incrementSlot());
    Bytes32 parentBlockRoot = invalidBlock.getMessage().hash_tree_root();
    for (int i = 0; i < invalidChainDepth; i++) {
      final UInt64 nextSlot = incrementSlot();
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(nextSlot.longValue(), parentBlockRoot);
      invalidBlockDescendants.add(block);
      parentBlockRoot = block.getMessage().hash_tree_root();
    }

    // Gossip all blocks except the first two
    invalidBlockDescendants.subList(1, invalidChainDepth).stream()
        .map(GossipedBlockEvent::new)
        .forEach(localEventBus::post);
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth - 1);

    // Gossip invalid block, which should fail to import and be marked invalid
    localEventBus.post(new GossipedBlockEvent(invalidBlock));
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth - 1);

    // Gossip the child of the invalid block, which should also be marked invalid causing
    // the rest of the chain to be marked invalid and dropped
    localEventBus.post(new GossipedBlockEvent(invalidBlockDescendants.get(0)));
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // If any invalid block is again gossiped, it should be ignored
    invalidBlockDescendants.stream().map(GossipedBlockEvent::new).forEach(localEventBus::post);
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImported_withPendingFutureBlocks() throws Exception {
    final int blockCount = 3;
    final List<SignedBeaconBlock> blocks = new ArrayList<>(blockCount);

    // Update local slot to match the first new block
    incrementSlot();
    for (int i = 0; i < blockCount; i++) {
      final UInt64 nextSlot = genesisSlot.plus(UInt64.valueOf(i + 1));
      blocks.add(remoteChain.createAndImportBlockAtSlot(nextSlot));
    }

    // Gossip all blocks except the first
    blocks.subList(1, blockCount).stream()
        .map(GossipedBlockEvent::new)
        .forEach(localEventBus::post);
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import next block, causing next block to be queued for import
    final SignedBeaconBlock firstBlock = blocks.get(0);
    assertThat(blockImporter.importBlock(firstBlock).get().isSuccessful()).isTrue();
    assertThat(importedBlocks.get()).containsExactly(firstBlock);
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(1);

    // Increment slot so that we can import the next block
    incrementSlot();
    assertThat(importedBlocks.get()).containsExactly(firstBlock, blocks.get(1));
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);

    // Increment slot so that we can import the next block
    incrementSlot();
    assertThat(importedBlocks.get()).containsExactlyElementsOf(blocks);
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(0);
  }

  private UInt64 incrementSlot() {
    currentSlot = currentSlot.plus(UInt64.ONE);
    localChain.setSlot(currentSlot);
    blockManager.onSlot(currentSlot);
    return currentSlot;
  }
}
