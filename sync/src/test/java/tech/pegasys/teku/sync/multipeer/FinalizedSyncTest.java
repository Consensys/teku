/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.sync.multipeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.sync.multipeer.BatchImporter.BatchImportResult.IMPORTED_ALL_BLOCKS;
import static tech.pegasys.teku.sync.multipeer.BatchImporter.BatchImportResult.IMPORT_FAILED;
import static tech.pegasys.teku.sync.multipeer.batches.BatchAssert.assertThatBatch;
import static tech.pegasys.teku.sync.multipeer.chains.TargetChainTestUtil.chainWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.sync.multipeer.batches.Batch;
import tech.pegasys.teku.sync.multipeer.batches.BatchFactory;
import tech.pegasys.teku.sync.multipeer.batches.EventThreadOnlyBatch;
import tech.pegasys.teku.sync.multipeer.batches.StubBatch;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;
import tech.pegasys.teku.util.config.StateStorageMode;

class FinalizedSyncTest {
  private final UInt64 BATCH_SIZE = UInt64.valueOf(25);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final TargetChain targetChain =
      chainWith(new SlotAndBlockRoot(UInt64.valueOf(1000), dataStructureUtil.randomBytes32()));
  private final InlineEventThread eventThread = new InlineEventThread();

  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.PRUNE);
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final BatchImporter batchImporter = mock(BatchImporter.class);
  private final BatchFactory batchFactory = mock(BatchFactory.class);
  private final Map<StubBatch, Batch> wrappedBatches = new HashMap<>();
  private final List<StubBatch> batches = new ArrayList<>();

  private final FinalizedSync sync =
      new FinalizedSync(eventThread, recentChainData, batchImporter, batchFactory, BATCH_SIZE);

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    when(batchImporter.importBatch(any()))
        .thenAnswer(
            invocation ->
                ((EventThreadOnlyBatch) invocation.getArgument(0)).getDelegate().getImportResult());
    when(batchFactory.createBatch(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              final TargetChain targetChain = invocation.getArgument(0);
              final UInt64 startSlot = invocation.getArgument(1);
              final UInt64 count = invocation.getArgument(2);
              final StubBatch batch = new StubBatch(targetChain, startSlot, count);
              assertThat(targetChain).isEqualTo(targetChain);
              assertThat(count).isEqualTo(BATCH_SIZE);
              // Enforce that all access by production code to the batch is on the event thread
              // Test code can just use it directly.
              final EventThreadOnlyBatch wrappedBatch =
                  new EventThreadOnlyBatch(eventThread, batch);
              batches.add(batch);
              wrappedBatches.put(batch, wrappedBatch);
              return wrappedBatch;
            });
  }

  @Test
  void shouldStartSyncFromEmptyDatabase() {
    sync.syncToChain(targetChain);

    assertThat(batches).hasSize(5);
    // Should start from the slot after our finalized epoch
    assertThat(batches.get(0).getFirstSlot()).isEqualTo(ONE);
    assertThat(batches.get(1).getFirstSlot()).isEqualTo(BATCH_SIZE.plus(1));
    assertThat(batches.get(2).getFirstSlot()).isEqualTo(BATCH_SIZE.times(2).plus(1));
    assertThat(batches.get(3).getFirstSlot()).isEqualTo(BATCH_SIZE.times(3).plus(1));
    assertThat(batches.get(4).getFirstSlot()).isEqualTo(BATCH_SIZE.times(4).plus(1));
  }

  @Test
  void shouldImportFirstBatchWhenSecondBatchFormsChain() {
    final SignedBlockAndState block5 = chainBuilder.generateBlockAtSlot(5);
    final SignedBlockAndState block26 = chainBuilder.generateBlockAtSlot(26);
    sync.syncToChain(targetChain);

    // First two batches come back, each with a block that matches correctly
    final StubBatch batch1 = batches.get(0);
    batch1.receiveBlocks(block5.getBlock());
    batches.get(1).receiveBlocks(block26.getBlock());

    // Batch1 should now be complete and import
    assertThatBatch(batch1).isComplete();
    assertThatBatch(batch1).isConfirmed();
    assertBatchesImported(batch1);
  }

  @Test
  void shouldMarkEmptyBatchesAsCompleteAndConfirmedWhenLaterBatchMatchesChainStart() {
    final SignedBeaconBlock block = chainBuilder.generateBlockAtSlot(BATCH_SIZE.plus(1)).getBlock();
    sync.syncToChain(targetChain);

    final StubBatch batch0 = batches.get(0);
    final StubBatch batch1 = batches.get(1);
    // First batch is empty
    batch0.receiveBlocks();
    // Second batch contains the first block
    batch1.receiveBlocks(block);

    assertThatBatch(batch0).isConfirmedAsEmpty();
  }

  @Test
  @Disabled
  void shouldResumeSyncFromSomewhereAfterRestart() {
    // TODO: Start from something other than genesis
  }

  @Test
  @Disabled
  void shouldNotReimportBlocksFromFinalizedEpoch() {
    // Do we need to start from the beginning or end of the finalized epoch?
  }

  @Test
  @Disabled
  void shouldNotRequestBlocksPastTargetChainHead() {}

  @Test
  void shouldRejectFirstBatchIfItDoesNotBuildOnKnownBlock() {
    sync.syncToChain(targetChain);

    final StubBatch firstBatch = batches.get(0);
    firstBatch.receiveBlocks(dataStructureUtil.randomSignedBeaconBlock(1));

    assertThatBatch(firstBatch).isInvalid();
  }

  @Test
  void shouldNotImportBatchUntilConfirmed() {
    final SignedBeaconBlock lastBlockOfFirstBatch =
        chainBuilder.generateBlockAtSlot(BATCH_SIZE.plus(1)).getBlock();
    sync.syncToChain(targetChain);

    final StubBatch firstBatch = batches.get(0);
    firstBatch.receiveBlocks(lastBlockOfFirstBatch);

    assertThatBatch(firstBatch).isComplete();
    assertThatBatch(firstBatch).isNotConfirmed();

    // Not imported yet because only the start has been matched
    assertNoBatchesImported();
  }

  @Test
  void shouldConfirmLaterBatchWhenPreviousAndNextBatchFormChain() {
    chainBuilder.generateBlockAtSlot(1);
    sync.syncToChain(targetChain);

    final StubBatch batch1 = batches.get(1);
    final StubBatch batch2 = batches.get(2);
    final StubBatch batch3 = batches.get(3);

    // Batch 0 hasn't returned any blocks yet, but we have 1,2 and 3 so can confirm batch 2 fits
    batch1.receiveBlocks(chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batch2.receiveBlocks(chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());
    batch3.receiveBlocks(chainBuilder.generateBlockAtSlot(batch3.getFirstSlot()).getBlock());

    assertThatBatch(batch2).isConfirmed();

    assertNoBatchesImported();
  }

  @Test
  void shouldImportPreviouslyConfirmedBatchesWhenEarlierBatchConfirmed() {
    final SignedBeaconBlock block1 = chainBuilder.generateBlockAtSlot(1).getBlock();
    sync.syncToChain(targetChain);

    final StubBatch batch0 = batches.get(0);
    final StubBatch batch1 = batches.get(1);
    final StubBatch batch2 = batches.get(2);
    final StubBatch batch3 = batches.get(3);

    // Batch 0 hasn't returned any blocks yet, but we have 1,2 and 3 so can confirm batch 2 fits
    batch1.receiveBlocks(chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batch2.receiveBlocks(chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());
    batch3.receiveBlocks(chainBuilder.generateBlockAtSlot(batch3.getFirstSlot()).getBlock());

    assertThatBatch(batch0).isNotConfirmed();
    assertThatBatch(batch1).isNotConfirmed();
    assertThatBatch(batch2).isConfirmed();
    assertThatBatch(batch3).isNotConfirmed();

    assertNoBatchesImported();

    // Then we get the request for batch0 back
    batch0.receiveBlocks(block1);

    assertThatBatch(batch0).isConfirmed();
    assertThatBatch(batch1).isConfirmed();
    assertThatBatch(batch2).isConfirmed();
    assertThatBatch(batch3).isNotConfirmed();
    assertBatchesImported(batch0, batch1, batch2);
  }

  @Test
  void shouldMarkBatchAsContestedWhenNextBatchDoesNotLineUp() {
    final SignedBeaconBlock block1 = chainBuilder.generateBlockAtSlot(1).getBlock();
    sync.syncToChain(targetChain);

    final StubBatch batch0 = batches.get(0);
    final StubBatch batch1 = batches.get(1);

    // Receive blocks that don't line up
    batch0.receiveBlocksAndMarkComplete(block1);
    batch1.receiveBlocks(dataStructureUtil.randomSignedBeaconBlock(BATCH_SIZE.plus(1)));

    assertNoBatchesImported();
    assertThatBatch(batch0).isContested();
    assertThatBatch(batch1).isContested();
  }

  @Test
  @Disabled
  void shouldMarkBatchAsInvalidWhenNextBatchDoesNotLineUpAndAlreadyContested() {}

  @Test
  @Disabled
  void shouldLimitTheNumberOfBatchesRequestedAtAnyOneTime() {}

  @Test
  void shouldMarkAllBatchesInChainAsInvalidWhenBlockFailsToImport() {
    sync.syncToChain(targetChain);

    final StubBatch batch0 = batches.get(0);
    final StubBatch batch1 = batches.get(1);
    final StubBatch batch2 = batches.get(2);
    final StubBatch batch3 = batches.get(3);

    // Receive a sequence of blocks that all form a chain
    batch0.receiveBlocks(chainBuilder.generateBlockAtSlot(1).getBlock());
    batch1.receiveBlocks(chainBuilder.generateBlockAtSlot(BATCH_SIZE.plus(1)).getBlock());
    batch2.receiveBlocks(chainBuilder.generateBlockAtSlot(BATCH_SIZE.times(2).plus(1)).getBlock());
    // Batch3 is on a different chain
    batch3.receiveBlocks(dataStructureUtil.randomSignedBeaconBlock(BATCH_SIZE.times(3).plus(1)));

    // But then it turns out that a block in batch1 was invalid
    batch0.getImportResult().complete(IMPORT_FAILED);

    // So batches 0, 1 and 2 are all invalid because they form a chain.
    assertThatBatch(batch0).isInvalid();
    assertThatBatch(batch1).isInvalid();
    assertThatBatch(batch2).isInvalid();

    // Batch 3 is still unknown because it didn't line up with the others
    assertThatBatch(batch3).isNotInvalid();
    assertThatBatch(batch3).isNotContested();

    // The batches are still active because they haven't been successfully imported
    assertBatchActive(batch0);
  }

  @Test
  void shouldRemoveBatchFromActiveSetWhenImportCompletesSuccessfully() {
    sync.syncToChain(targetChain);

    final StubBatch batch0 = batches.get(0);
    final StubBatch batch1 = batches.get(1);
    batch0.receiveBlocks(chainBuilder.generateBlockAtSlot(1).getBlock());
    batch1.receiveBlocks(chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());

    assertBatchesImported(batch0);

    batch0.getImportResult().complete(IMPORTED_ALL_BLOCKS);

    assertBatchNotActive(batch0);
  }

  @Test
  @Disabled
  void shouldSwitchChains() {
    // Optimistically assume the new chain is an extension of the old one and just keep adding
    // batches of block to the end
  }

  @Test
  @Disabled
  void shouldNotInvalidateBatchesFromNewChainThatDoesNotLineUpWithBatchFromOldChain() {}

  @Test
  @Disabled
  void shouldRestartSyncFromFinalizedCheckpointWhenBatchFromNewChainDoesNotLineUp() {}

  @Test
  void shouldProgressWhenThereAreManyEmptyBatchesInARow() {
    sync.syncToChain(targetChain);

    final int initialBatchCount = batches.size();
    // All the requested batches are empty
    //noinspection ForLoopReplaceableByForEach (would lead to ConcurrentModificationException)
    for (int i = 0; i < initialBatchCount; i++) {
      batches.get(i).receiveBlocks();
    }

    // But because they're all empty, it should request more batches
    assertThat(batches).hasSizeGreaterThan(initialBatchCount);

    // And then we get one back with a valid block
    final StubBatch laterBatch = batches.get(initialBatchCount);
    laterBatch.receiveBlocks(
        chainBuilder.generateBlockAtSlot(laterBatch.getFirstSlot()).getBlock());

    // But nothing gets imported yet because it isn't confirmed.
    verifyNoInteractions(batchImporter);
    assertBatchActive(batches.get(0));

    // Finally it's confirmed
    final StubBatch confirmingBatch = batches.get(initialBatchCount + 1);
    confirmingBatch.receiveBlocks(
        chainBuilder.generateBlockAtSlot(confirmingBatch.getFirstSlot()).getBlock());

    // So all the batches get imported, but because there's no point importing empty batches
    // only laterBatch is passed to the BatchImporter
    assertBatchesImported(laterBatch);

    // And when it completes it and all the earlier empty batches are dropped
    laterBatch.getImportResult().complete(IMPORTED_ALL_BLOCKS);
    batches.subList(0, initialBatchCount + 1).forEach(this::assertBatchNotActive);
  }

  private void assertBatchNotActive(final StubBatch batch) {
    // Need to use the wrapped batch which enforces usage of event thread
    eventThread.execute(() -> assertThat(sync.isActiveBatch(wrappedBatches.get(batch))).isFalse());
  }

  private void assertBatchActive(final StubBatch batch) {
    // Need to use the wrapped batch which enforces usage of event thread
    eventThread.execute(() -> assertThat(sync.isActiveBatch(wrappedBatches.get(batch))).isTrue());
  }

  private void assertBatchesImported(final StubBatch... batches) {
    Stream.of(batches)
        .forEach(batch -> verify(batchImporter).importBatch(wrappedBatches.get(batch)));
    verifyNoMoreInteractions(batchImporter);
  }

  private void assertNoBatchesImported() {
    verifyNoInteractions(batchImporter);
  }
}
