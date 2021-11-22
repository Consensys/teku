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

package tech.pegasys.teku.statetransition.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityFactory;

@SuppressWarnings("FutureReturnValueIgnored")
public class BlockManagerTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(2);
  private final BlockImportNotifications blockImportNotifications =
      mock(BlockImportNotifications.class);
  private final UInt64 historicalBlockTolerance = UInt64.valueOf(5);
  private final UInt64 futureBlockTolerance = UInt64.valueOf(2);
  private final int maxPendingBlocks = 10;
  private final PendingPool<SignedBeaconBlock> pendingBlocks =
      PendingPool.createForBlocks(
          spec, historicalBlockTolerance, futureBlockTolerance, maxPendingBlocks);
  private final FutureItems<SignedBeaconBlock> futureBlocks =
      FutureItems.create(SignedBeaconBlock::getSlot);

  private final RecentChainData localRecentChainData =
      MemoryOnlyRecentChainData.builder().specProvider(spec).build();
  private final RecentChainData remoteRecentChainData =
      MemoryOnlyRecentChainData.builder().specProvider(spec).build();
  private final BeaconChainUtil localChain =
      BeaconChainUtil.create(localRecentChainData, validatorKeys);
  private final BeaconChainUtil remoteChain =
      BeaconChainUtil.create(remoteRecentChainData, validatorKeys);
  private final ForkChoice forkChoice =
      ForkChoice.create(
          spec, new InlineEventThread(), localRecentChainData, mock(ForkChoiceNotifier.class));

  private final BlockImporter blockImporter =
      new BlockImporter(
          blockImportNotifications,
          localRecentChainData,
          forkChoice,
          WeakSubjectivityFactory.lenientValidator(),
          ExecutionEngineChannel.NOOP);
  private final BlockManager blockManager =
      new BlockManager(
          localRecentChainData,
          blockImporter,
          pendingBlocks,
          futureBlocks,
          mock(BlockValidator.class));

  private UInt64 currentSlot = GENESIS_SLOT;

  @BeforeEach
  public void setup() {
    forwardBlockImportedNotificationsTo(blockManager);
    localChain.initializeStorage();
    remoteChain.initializeStorage();
    assertThat(blockManager.start()).isCompleted();
  }

  private void forwardBlockImportedNotificationsTo(final BlockManager blockManager) {
    doAnswer(
            invocation -> {
              blockManager.onBlockImported(invocation.getArgument(0));
              return null;
            })
        .when(blockImportNotifications)
        .onBlockImported(any());
  }

  @AfterEach
  public void cleanup() throws Exception {
    assertThat(blockManager.stop()).isCompleted();
  }

  @Test
  public void shouldImport() throws Exception {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock = localChain.createBlockAtSlot(nextSlot);
    incrementSlot();

    blockManager.importBlock(nextBlock).join();
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void shouldPutUnattachedBlockToPending() throws Exception {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final UInt64 nextNextSlot = nextSlot.plus(UInt64.ONE);
    // Create 2 blocks
    remoteChain.createAndImportBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock = remoteChain.createAndImportBlockAtSlot(nextNextSlot);

    incrementSlot();
    incrementSlot();
    blockManager.importBlock(nextNextBlock).join();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.contains(nextNextBlock)).isTrue();
  }

  @Test
  public void onGossipedBlock_retryIfParentWasUnknownButIsNowAvailable() throws Exception {
    final BlockImporter blockImporter = mock(BlockImporter.class);
    final RecentChainData localRecentChainData = mock(RecentChainData.class);
    final BlockManager blockManager =
        new BlockManager(
            localRecentChainData,
            blockImporter,
            pendingBlocks,
            futureBlocks,
            mock(BlockValidator.class));
    forwardBlockImportedNotificationsTo(blockManager);
    assertThat(blockManager.start()).isCompleted();

    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final UInt64 nextNextSlot = nextSlot.plus(UInt64.ONE);
    // Create 2 blocks
    remoteChain.createAndImportBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock = remoteChain.createAndImportBlockAtSlot(nextNextSlot);

    final SafeFuture<BlockImportResult> blockImportResult = new SafeFuture<>();
    when(blockImporter.importBlock(nextNextBlock))
        .thenReturn(blockImportResult)
        .thenReturn(new SafeFuture<>());

    incrementSlot();
    incrementSlot();
    blockManager.importBlock(nextNextBlock);
    ignoreFuture(verify(blockImporter).importBlock(nextNextBlock));

    // Before nextNextBlock imports, it's parent becomes available
    when(localRecentChainData.containsBlock(nextNextBlock.getParentRoot())).thenReturn(true);

    // So when the block import completes, it should be retried
    blockImportResult.complete(BlockImportResult.FAILED_UNKNOWN_PARENT);
    ignoreFuture(verify(blockImporter, times(2)).importBlock(nextNextBlock));

    assertThat(pendingBlocks.contains(nextNextBlock)).isFalse();
  }

  @Test
  public void onGossipedBlock_futureBlock() throws Exception {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock = remoteChain.createAndImportBlockAtSlot(nextSlot);

    blockManager.importBlock(nextBlock).join();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(nextBlock)).isTrue();
  }

  @Test
  public void onGossipedBlock_unattachedFutureBlock() throws Exception {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final UInt64 nextNextSlot = nextSlot.plus(UInt64.ONE);
    // Create 2 blocks
    remoteChain.createAndImportBlockAtSlot(nextSlot);
    final SignedBeaconBlock nextNextBlock = remoteChain.createAndImportBlockAtSlot(nextNextSlot);

    incrementSlot();
    blockManager.importBlock(nextNextBlock).join();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(0);
    assertThat(pendingBlocks.contains(nextNextBlock)).isTrue();
  }

  @Test
  public void onProposedBlock_shouldImport() throws Exception {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock = localChain.createBlockAtSlot(nextSlot);
    incrementSlot();

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onProposedBlock_futureBlock() throws Exception {
    final UInt64 nextSlot = GENESIS_SLOT.plus(UInt64.ONE);
    final SignedBeaconBlock nextBlock = remoteChain.createAndImportBlockAtSlot(nextSlot);

    assertThat(blockManager.importBlock(nextBlock)).isCompleted();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.contains(nextBlock)).isTrue();
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
    blocks.subList(1, blockCount).stream().forEach(blockManager::importBlock);
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import next block, causing remaining blocks to be imported
    assertThat(blockImporter.importBlock(blocks.get(0)).get().isSuccessful()).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImportFailure_withPendingDependantBlocks() throws Exception {
    final int invalidChainDepth = 3;
    final List<SignedBeaconBlock> invalidBlockDescendants = new ArrayList<>(invalidChainDepth);

    final SignedBeaconBlock invalidBlock =
        remoteChain.createBlockAtSlotFromInvalidProposer(incrementSlot());
    Bytes32 parentBlockRoot = invalidBlock.getMessage().hashTreeRoot();
    for (int i = 0; i < invalidChainDepth; i++) {
      final UInt64 nextSlot = incrementSlot();
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(nextSlot.longValue(), parentBlockRoot);
      invalidBlockDescendants.add(block);
      parentBlockRoot = block.getMessage().hashTreeRoot();
    }

    // Gossip all blocks except the first
    invalidBlockDescendants.stream().forEach(blockManager::importBlock);
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth);

    // Gossip next block, causing dependent blocks to be dropped when the import fails
    blockManager.importBlock(invalidBlock).join();
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // If any invalid block is again gossiped, it should be ignored
    invalidBlockDescendants.stream().forEach(blockManager::importBlock);
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImportFailure_withUnconnectedPendingDependantBlocks() throws Exception {
    final int invalidChainDepth = 3;
    final List<SignedBeaconBlock> invalidBlockDescendants = new ArrayList<>(invalidChainDepth);

    final SignedBeaconBlock invalidBlock =
        remoteChain.createBlockAtSlotFromInvalidProposer(incrementSlot());
    Bytes32 parentBlockRoot = invalidBlock.getMessage().hashTreeRoot();
    for (int i = 0; i < invalidChainDepth; i++) {
      final UInt64 nextSlot = incrementSlot();
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(nextSlot.longValue(), parentBlockRoot);
      invalidBlockDescendants.add(block);
      parentBlockRoot = block.getMessage().hashTreeRoot();
    }

    // Gossip all blocks except the first two
    invalidBlockDescendants.subList(1, invalidChainDepth).stream()
        .forEach(blockManager::importBlock);
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth - 1);

    // Gossip invalid block, which should fail to import and be marked invalid
    blockManager.importBlock(invalidBlock);
    assertThat(pendingBlocks.size()).isEqualTo(invalidChainDepth - 1);

    // Gossip the child of the invalid block, which should also be marked invalid causing
    // the rest of the chain to be marked invalid and dropped
    blockManager.importBlock(invalidBlockDescendants.get(0));
    assertThat(pendingBlocks.size()).isEqualTo(0);

    // If any invalid block is again gossiped, it should be ignored
    invalidBlockDescendants.stream().forEach(blockManager::importBlock);
    assertThat(pendingBlocks.size()).isEqualTo(0);
  }

  @Test
  public void onBlockImported_withPendingFutureBlocks() throws Exception {
    final int blockCount = 3;
    final List<SignedBeaconBlock> blocks = new ArrayList<>(blockCount);

    // Update local slot to match the first new block
    incrementSlot();
    for (int i = 0; i < blockCount; i++) {
      final UInt64 nextSlot = GENESIS_SLOT.plus(i + 1);
      blocks.add(remoteChain.createAndImportBlockAtSlot(nextSlot));
    }

    // Gossip all blocks except the first
    blocks.subList(1, blockCount).stream().forEach(blockManager::importBlock);
    assertThat(pendingBlocks.size()).isEqualTo(blockCount - 1);

    // Import next block, causing next block to be queued for import
    final SignedBeaconBlock firstBlock = blocks.get(0);
    assertThat(blockImporter.importBlock(firstBlock).get().isSuccessful()).isTrue();
    assertThat(pendingBlocks.size()).isEqualTo(1);
    assertThat(futureBlocks.size()).isEqualTo(1);

    // Increment slot so that we can import the next block
    incrementSlot();
    assertThat(pendingBlocks.size()).isEqualTo(0);
    assertThat(futureBlocks.size()).isEqualTo(1);

    // Increment slot so that we can import the next block
    incrementSlot();
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
