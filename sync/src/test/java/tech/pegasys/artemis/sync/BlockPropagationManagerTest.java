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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.networking.eth2.gossip.events.GossipedBlockEvent;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.ImportedBlocks;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.Constants;

public class BlockPropagationManagerTest {
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(2);
  private final EventBus localEventBus = new EventBus();
  private final EventBus remoteEventBus = new EventBus();
  private final UnsignedLong historicalBlockTolerance = UnsignedLong.valueOf(5);
  private final UnsignedLong futureBlockTolerance = UnsignedLong.valueOf(2);
  private final PendingPool<SignedBeaconBlock> pendingBlocks =
      PendingPool.createForBlocks(localEventBus, historicalBlockTolerance, futureBlockTolerance);
  private final FutureItems<SignedBeaconBlock> futureBlocks =
      new FutureItems<>(SignedBeaconBlock::getSlot);

  private final ChainStorageClient localStorage =
      ChainStorageClient.memoryOnlyClient(localEventBus);
  private final ChainStorageClient remoteStorage =
      ChainStorageClient.memoryOnlyClient(remoteEventBus);
  private final BeaconChainUtil localChain = BeaconChainUtil.create(localStorage, validatorKeys);
  private final BeaconChainUtil remoteChain = BeaconChainUtil.create(remoteStorage, validatorKeys);
  private final ImportedBlocks importedBlocks = new ImportedBlocks(localEventBus);

  private final BlockImporter blockImporter = new BlockImporter(localStorage, localEventBus);
  private final BlockPropagationManager blockPropagationManager =
      new BlockPropagationManager(
          localEventBus, localStorage, blockImporter, pendingBlocks, futureBlocks);

  private final UnsignedLong genesisSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);
  private UnsignedLong currentSlot = genesisSlot;

  @BeforeEach
  public void setup() {
    localChain.initializeStorage();
    remoteChain.initializeStorage();
    assertThat(blockPropagationManager.start()).isCompleted();
  }

  @AfterEach
  public void cleanup() throws Exception {
    assertThat(blockPropagationManager.stop()).isCompleted();
    importedBlocks.close();
  }

  @Test
  public void onGossipedBlock_shouldImport() throws Exception {
    final UnsignedLong nextSlot = genesisSlot.plus(UnsignedLong.ONE);
    final SignedBeaconBlock nextBlock = localChain.createBlockAtSlot(nextSlot);
    incrementSlot();

    assertThat(importedBlocks.get()).isEmpty();
    localEventBus.post(new GossipedBlockEvent(nextBlock));
    assertThat(importedBlocks.get()).containsExactly(nextBlock);
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onGossipedBlock_unattachedBlock() throws Exception {
    final UnsignedLong nextSlot = genesisSlot.plus(UnsignedLong.ONE);
    final UnsignedLong nextNextSlot = nextSlot.plus(UnsignedLong.ONE);
    // Create 2 blocks
    remoteChain.createAndImportBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock =
        remoteChain.createAndImportBlockAtSlot(nextNextSlot).getBlock();

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
    final UnsignedLong nextSlot = genesisSlot.plus(UnsignedLong.ONE);
    final SignedBeaconBlock nextBlock = remoteChain.createAndImportBlockAtSlot(nextSlot).getBlock();

    localEventBus.post(new GossipedBlockEvent(nextBlock));
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(nextBlock)).isTrue();
  }

  @Test
  public void onGossipedBlock_unattachedFutureBlock() throws Exception {
    final UnsignedLong nextSlot = genesisSlot.plus(UnsignedLong.ONE);
    final UnsignedLong nextNextSlot = nextSlot.plus(UnsignedLong.ONE);
    // Create 2 blocks
    remoteChain.createAndImportBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock =
        remoteChain.createAndImportBlockAtSlot(nextNextSlot).getBlock();

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
      final UnsignedLong nextSlot = incrementSlot();
      blocks.add(remoteChain.createAndImportBlockAtSlot(nextSlot).getBlock());
    }

    // Gossip all blocks except the first
    blocks.subList(1, blockCount).stream()
        .map(GossipedBlockEvent::new)
        .forEach(localEventBus::post);
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import next block, causing remaining blocks to be imported
    assertThat(blockImporter.importBlock(blocks.get(0)).isSuccessful()).isTrue();
    assertThat(importedBlocks.get()).containsExactlyElementsOf(blocks);
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImported_withPendingFutureBlocks() throws Exception {
    final int blockCount = 3;
    final List<SignedBeaconBlock> blocks = new ArrayList<>(blockCount);

    // Update local slot to match the first new block
    incrementSlot();
    for (int i = 0; i < blockCount; i++) {
      final UnsignedLong nextSlot = genesisSlot.plus(UnsignedLong.valueOf(i + 1));
      blocks.add(remoteChain.createAndImportBlockAtSlot(nextSlot).getBlock());
    }

    // Gossip all blocks except the first
    blocks.subList(1, blockCount).stream()
        .map(GossipedBlockEvent::new)
        .forEach(localEventBus::post);
    assertThat(importedBlocks.get()).isEmpty();
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import next block, causing next block to be queued for import
    final SignedBeaconBlock firstBlock = blocks.get(0);
    assertThat(blockImporter.importBlock(firstBlock).isSuccessful()).isTrue();
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

  private UnsignedLong incrementSlot() {
    currentSlot = currentSlot.plus(UnsignedLong.ONE);
    localChain.setSlot(currentSlot);
    localEventBus.post(new SlotEvent(currentSlot));
    return currentSlot;
  }
}
