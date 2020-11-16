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

package tech.pegasys.teku.sync.forward.multipeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.sync.forward.multipeer.BatchImporter.BatchImportResult.IMPORTED_ALL_BLOCKS;
import static tech.pegasys.teku.sync.forward.multipeer.BatchImporter.BatchImportResult.IMPORT_FAILED;
import static tech.pegasys.teku.sync.forward.multipeer.batches.BatchAssert.assertThatBatch;
import static tech.pegasys.teku.sync.forward.multipeer.chains.TargetChainTestUtil.chainWith;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.sync.forward.multipeer.batches.StubBatchFactory;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChains;
import tech.pegasys.teku.util.config.StateStorageMode;

class BatchSyncTest {
  private final UInt64 BATCH_SIZE = UInt64.valueOf(25);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final InlineEventThread eventThread = new InlineEventThread();

  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.PRUNE);
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final SyncSource syncSource = mock(SyncSource.class);
  private final BatchImporter batchImporter = mock(BatchImporter.class);
  private final StubBatchFactory batches = new StubBatchFactory(eventThread, true);

  private TargetChain targetChain =
      chainWith(
          new SlotAndBlockRoot(UInt64.valueOf(1000), dataStructureUtil.randomBytes32()),
          syncSource);
  private final MultipeerCommonAncestorFinder commonAncestor =
      mock(MultipeerCommonAncestorFinder.class);

  private final BatchSync sync =
      BatchSync.create(
          eventThread, recentChainData, batchImporter, batches, BATCH_SIZE, commonAncestor);

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    when(batchImporter.importBatch(any()))
        .thenAnswer(invocation -> batches.getImportResult(invocation.getArgument(0)));
    when(commonAncestor.findCommonAncestor(any()))
        .thenAnswer(
            invocation ->
                completedFuture(compute_start_slot_at_epoch(recentChainData.getFinalizedEpoch())));
  }

  @Test
  void shouldStartSyncFromEmptyDatabase() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThat(batches).hasSize(5);

    // Should start from the slot after our finalized epoch
    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);
    final Batch batch4 = batches.get(4);
    assertThatBatch(batch0).hasFirstSlot(ONE);
    assertThatBatch(batch1).hasFirstSlot(batch0.getLastSlot().plus(1));
    assertThatBatch(batch2).hasFirstSlot(batch1.getLastSlot().plus(1));
    assertThatBatch(batch3).hasFirstSlot(batch2.getLastSlot().plus(1));
    assertThatBatch(batch4).hasFirstSlot(batch3.getLastSlot().plus(1));
    batches.forEach(
        batch ->
            assertThatBatch(batch).hasLastSlot(batch.getFirstSlot().plus(BATCH_SIZE).minus(1)));
  }

  @Test
  void shouldImportFirstBatchWhenSecondBatchFormsChain() {
    final SignedBlockAndState block5 = chainBuilder.generateBlockAtSlot(5);
    final SignedBlockAndState block26 = chainBuilder.generateBlockAtSlot(26);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    // First two batches come back, each with a block that matches correctly
    final Batch batch1 = batches.get(0);
    batches.receiveBlocks(batch1, block5.getBlock());
    batches.receiveBlocks(batches.get(1), block26.getBlock());

    // Batch1 should now be complete and import
    assertThatBatch(batch1).isComplete();
    assertThatBatch(batch1).isConfirmed();
    assertBatchImported(batch1);
  }

  @Test
  void shouldMarkEmptyBatchesAsCompleteAndConfirmedWhenLaterBatchMatchesChainStart() {
    final SignedBeaconBlock block = chainBuilder.generateBlockAtSlot(BATCH_SIZE.plus(1)).getBlock();
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    // First batch is empty
    batches.receiveBlocks(batch0);
    // Second batch contains the first block
    batches.receiveBlocks(batch1, block);

    assertThatBatch(batch0).isConfirmedAsEmpty();
  }

  @Test
  void shouldResumeSyncFromCommonAncestorAfterRestart() {
    storageSystem.chainUpdater().finalizeEpoch(ONE);
    final UInt64 commonAncestorSlot = UInt64.valueOf(50);
    when(commonAncestor.findCommonAncestor(targetChain))
        .thenReturn(completedFuture(commonAncestorSlot));
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThatBatch(batches.get(0)).hasFirstSlot(commonAncestorSlot.plus(1));
  }

  @Test
  void shouldFailSyncWhenTargetChainHasNoPeersAndThereAreNoOutstandingRequests() {
    final TargetChains targetChains = new TargetChains();
    targetChains.onPeerStatusUpdated(syncSource, targetChain.getChainHead());
    targetChain = targetChains.streamChains().findFirst().orElseThrow();

    // Start the sync
    final SafeFuture<SyncResult> result = sync.syncToChain(targetChain);

    // Then the last peer is moved off that chain but we keep waiting for pending requests
    targetChains.onPeerDisconnected(syncSource);
    assertThat(result).isNotDone();
    final int originalBatchCount = batches.size();

    // Next time the sync progresses, it aborts because there are no more peers.
    batches.receiveBlocks(batches.get(0));
    assertThat(result).isCompletedWithValue(SyncResult.FAILED);
    assertThat(batches).hasSize(originalBatchCount); // No more batches created
  }

  @Test
  void shouldNotRequestBlocksPastTargetChainHead() {
    final UInt64 headSlot = BATCH_SIZE.times(3).minus(5);
    targetChain =
        chainWith(new SlotAndBlockRoot(headSlot, dataStructureUtil.randomBytes32()), syncSource);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThat(batches).hasSize(3);
    assertThatBatch(batches.get(2)).hasLastSlot(headSlot);
  }

  @Test
  void shouldConfirmAndImportBatchWhenItEndsWithTargetChainHeadBlock() {
    final UInt64 headSlot = BATCH_SIZE.times(2).minus(5);
    final SignedBeaconBlock block3 = chainBuilder.generateBlockAtSlot(3).getBlock();
    final SignedBeaconBlock headBlock = chainBuilder.generateBlockAtSlot(headSlot).getBlock();
    targetChain = chainWith(new SlotAndBlockRoot(headSlot, headBlock.getRoot()), syncSource);
    final SafeFuture<SyncResult> result = sync.syncToChain(targetChain);
    assertThat(result).isNotDone();

    assertThat(batches).hasSize(2);
    final Batch batch1 = batches.get(0);
    final Batch batch2 = batches.get(1);

    batches.receiveBlocks(batch1, block3);
    batches.receiveBlocks(batch2, headBlock);
    assertThat(result).isNotDone();

    // Both batches should be imported
    assertBatchImported(batch1);

    batches.getImportResult(batch1).complete(IMPORTED_ALL_BLOCKS);
    assertThat(result).isNotDone();

    assertBatchImported(batch2);
    batches.getImportResult(batch2).complete(IMPORTED_ALL_BLOCKS);
    assertThat(result).isCompletedWithValue(SyncResult.COMPLETE);
  }

  @Test
  void shouldMarkBatchInvalidWhenSlotIsTargetHeadSlotAndRootDoesNotMatch() {
    final UInt64 lastSlot = UInt64.valueOf(2);
    targetChain =
        chainWith(new SlotAndBlockRoot(lastSlot, dataStructureUtil.randomBytes32()), syncSource);

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    assertThatBatch(batch0).hasLastSlot(lastSlot);

    // We get a block in the last slot which doesn't match the target root
    // (it does match the starting point though)
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(lastSlot).getBlock());

    // So the batch must be invalid
    batches.assertMarkedInvalid(batch0);
  }

  @Test
  void shouldContestBatchesWhenLastBlockDoesNotMatchTargetAndHasOnlyEmptyBatchesAfterIt() {
    targetChain =
        chainWith(
            new SlotAndBlockRoot(BATCH_SIZE.times(2).minus(1), dataStructureUtil.randomBytes32()),
            syncSource);

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    assertThatBatch(batch1).hasLastSlot(targetChain.getChainHead().getSlot());

    // The last block we get doesn't match the target root, but then
    batches.receiveBlocks(
        batch0, chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock());
    batches.receiveBlocks(batch1);

    // We now have all the blocks but something doesn't match up so either the last block is wrong
    // or the following empty batch shouldn't have been empty.
    batches.assertMarkedContested(batch0);
    batches.assertMarkedContested(batch1);
  }

  @Test
  void shouldNotContestBatchesWhenAnIncompleteBatchIsFollowedByEmptyBatchesAtEndOfChain() {
    targetChain =
        chainWith(
            new SlotAndBlockRoot(BATCH_SIZE.times(2).minus(1), dataStructureUtil.randomBytes32()),
            syncSource);

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    assertThatBatch(batch1).hasLastSlot(targetChain.getChainHead().getSlot());

    // The last block we get doesn't match the target root, but then
    batches.receiveBlocks(
        batch0, chainBuilder.generateBlockAtSlot(batch0.getFirstSlot()).getBlock());
    batches.receiveBlocks(batch1);

    assertThatBatch(batch0).isNotContested();
    assertThatBatch(batch1).isNotContested();
  }

  @Test
  void shouldContestAllBatchesWhenEndSlotIsReachedWithNoBlocksReceived() {
    targetChain =
        chainWith(
            new SlotAndBlockRoot(BATCH_SIZE.times(2).minus(1), dataStructureUtil.randomBytes32()),
            syncSource);

    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThat(batches).hasSize(2);
    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    assertThatBatch(batch1).hasLastSlot(targetChain.getChainHead().getSlot());

    // The last block we get doesn't match the target root, but then
    batches.receiveBlocks(batch0);
    batches.receiveBlocks(batch1);

    batches.assertMarkedContested(batch0);
    batches.assertMarkedContested(batch1);
  }

  @Test
  void shouldRejectFirstBatchIfItDoesNotBuildOnKnownBlock() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch firstBatch = batches.get(0);
    batches.receiveBlocks(firstBatch, dataStructureUtil.randomSignedBeaconBlock(1));

    batches.assertMarkedInvalid(firstBatch);
  }

  @Test
  void shouldNotImportBatchUntilConfirmed() {
    final SignedBeaconBlock lastBlockOfFirstBatch =
        chainBuilder.generateBlockAtSlot(BATCH_SIZE).getBlock();
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch firstBatch = batches.get(0);
    batches.receiveBlocks(firstBatch, lastBlockOfFirstBatch);

    assertThatBatch(firstBatch).isComplete();
    assertThatBatch(firstBatch).isNotConfirmed();

    // Not imported yet because only the start has been matched
    assertNoBatchesImported();
  }

  @Test
  void shouldConfirmLaterBatchWhenPreviousAndNextBatchFormChain() {
    chainBuilder.generateBlockAtSlot(1);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);

    // Batch 0 hasn't returned any blocks yet, but we have 1,2 and 3 so can confirm batch 2 fits
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch3, chainBuilder.generateBlockAtSlot(batch3.getFirstSlot()).getBlock());

    assertThatBatch(batch2).isConfirmed();

    assertNoBatchesImported();
  }

  @Test
  void shouldImportPreviouslyConfirmedBatchesWhenEarlierBatchConfirmed() {
    final SignedBeaconBlock block1 = chainBuilder.generateBlockAtSlot(1).getBlock();
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);

    // Batch 0 hasn't returned any blocks yet, but we have 1,2 and 3 so can confirm batch 2 fits
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch3, chainBuilder.generateBlockAtSlot(batch3.getFirstSlot()).getBlock());

    assertThatBatch(batch0).isNotConfirmed();
    assertThatBatch(batch1).isNotConfirmed();
    assertThatBatch(batch2).isConfirmed();
    assertThatBatch(batch3).isNotConfirmed();

    assertNoBatchesImported();

    // Then we get the request for batch0 back
    batches.receiveBlocks(batch0, block1);

    assertThatBatch(batch0).isConfirmed();
    assertThatBatch(batch1).isConfirmed();
    assertThatBatch(batch2).isConfirmed();
    assertThatBatch(batch3).isNotConfirmed();
    assertBatchImported(batch0);
    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);
    assertBatchImported(batch1);
    batches.getImportResult(batch1).complete(IMPORTED_ALL_BLOCKS);
    assertBatchImported(batch2);
  }

  @Test
  void shouldNotMarkBatchAsContestedWhenNextBatchIsEmpty() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);

    final SignedBeaconBlock batch0Block =
        chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock();

    batches.receiveBlocks(batch0, batch0Block);
    batches.receiveBlocks(batch1);

    // Can't confirm either batch yet because the next block is still unknown but also not contested
    assertThatBatch(batch0).isNotContested();
    assertThatBatch(batch0).isNotConfirmed();
    assertThatBatch(batch1).isNotContested();
    assertThatBatch(batch1).isNotConfirmed();
  }

  @Test
  void shouldMarkBatchAsContestedWhenNextBatchDoesNotLineUp() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);

    final SignedBeaconBlock batch0Block =
        chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock();
    final SignedBeaconBlock batch1Block =
        chainBuilder.generateBlockAtSlot(batch1.getLastSlot()).getBlock();

    // Receive blocks that don't line up
    batches.receiveBlocks(batch0, batch0Block);
    batches.receiveBlocks(batch1, dataStructureUtil.randomSignedBeaconBlock(BATCH_SIZE.plus(1)));

    assertNoBatchesImported();
    batches.assertMarkedContested(batch0);
    batches.assertMarkedContested(batch1);

    // Both batches now request the same range from a different peer
    batches.receiveBlocks(batch0, batch0Block); // Batch 0 is unchanged
    batches.receiveBlocks(batch1, batch1Block); // Batch 1 now gives us valid data

    assertThatBatch(batch0).isConfirmed();
  }

  @Test
  void shouldNotMarkBatchesAsContestedWhenBlocksDoNotLineUpBecauseOfIncompleteBatchesBetween() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);

    final SignedBeaconBlock batch0Block =
        chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock();
    final SignedBeaconBlock batch1Block =
        chainBuilder.generateBlockAtSlot(batch1.getLastSlot()).getBlock();
    final SignedBeaconBlock batch2Block =
        chainBuilder.generateBlockAtSlot(batch2.getLastSlot()).getBlock();

    // Receive blocks from batch 0 and 2 first which won't line up because batch1 is still missing
    batches.receiveBlocks(batch0, batch0Block);
    batches.receiveBlocks(batch2, batch2Block);

    assertNoBatchesImported();
    assertThatBatch(batch0).isNotContested();
    assertThatBatch(batch1).isNotContested();
    assertThatBatch(batch2).isNotContested();

    // Then when batch 1 arrives, everything lines up.
    batches.receiveBlocks(batch1, batch1Block);

    assertThatBatch(batch0).isConfirmed();
    assertThatBatch(batch1).isConfirmed();
    assertThatBatch(batch2).hasConfirmedFirstBlock();
  }

  @Test
  void shouldNotResetOnChainSwitchWhenBlocksDoNotLineUpBecauseOfIncompleteBatches() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    targetChain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(2000), dataStructureUtil.randomBytes32()),
            syncSource);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);

    final SignedBeaconBlock batch0Block =
        chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock();
    // Generate a block between batch 0 and 5 that we won't have
    chainBuilder.generateBlockAtSlot(batch2.getLastSlot());

    // Receive block from batch 0 so we have a block from the first chain to compare to
    batches.receiveBlocks(batch0, batch0Block);
    // Batch 1 is empty to trigger requesting a new batch from the new chain
    batches.receiveBlocks(batch1);

    // Get the first batch from the new chain
    final Batch batch5 = batches.get(5);
    assertThat(batch5.getTargetChain()).isEqualTo(targetChain);
    final SignedBeaconBlock batch5Block =
        chainBuilder.generateBlockAtSlot(batch5.getLastSlot()).getBlock();

    // Receive first blocks from new chain which won't line up because batches are still incomplete
    batches.receiveBlocks(batch5, batch5Block);

    assertNoBatchesImported();
    assertThatBatch(batch0).isNotContested();
    assertThatBatch(batch1).isNotContested();
    assertThatBatch(batch5).isNotContested();

    // Should still be optimistically assuming the chains join up
    assertBatchActive(batch0);
    assertBatchActive(batch1);
    assertBatchActive(batch5);
  }

  @Test
  void shouldLimitTheNumberOfBatchesWithBlocksPendingImport() {
    // Avoid the queue of blocks to import getting too long
    // but allow any number of empty batches since we can only confirm blocks, not empty batches
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    assertThat(batches).hasSize(5);

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);

    batches.receiveBlocks(
        batch0, chainBuilder.generateBlockAtSlot(batch0.getLastSlot()).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getLastSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getLastSlot()).getBlock());

    // Don't create more batches even though some are complete because we haven't imported any
    assertThat(batches).hasSize(5);

    // But finding an empty batch allows us to request another one
    batches.receiveBlocks(batch3);
    assertThat(batches).hasSize(6);

    // And when the first batch completes importing, we can request another one
    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);
    assertThat(batches).hasSize(7);
  }

  @Test
  void shouldMarkAllBatchesInChainAsInvalidWhenBlockFailsToImport() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    final Batch batch3 = batches.get(3);

    // Receive a sequence of blocks that all form a chain
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(batch1, chainBuilder.generateBlockAtSlot(BATCH_SIZE.plus(1)).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(BATCH_SIZE.times(2).plus(1)).getBlock());
    // Batch3 is on a different chain
    batches.receiveBlocks(
        batch3, dataStructureUtil.randomSignedBeaconBlock(BATCH_SIZE.times(3).plus(1)));

    // But then it turns out that a block in batch1 was invalid
    batches.getImportResult(batch0).complete(IMPORT_FAILED);

    // So batches 0, 1 and 2 are all invalid because they form a chain.
    batches.assertMarkedInvalid(batch0);
    batches.assertMarkedInvalid(batch1);
    batches.assertMarkedInvalid(batch2);

    // Batch 3 is still unknown because it didn't line up with the others
    batches.assertNotMarkedInvalid(batch3);
    assertThatBatch(batch3).isNotContested();

    // The batches are still active because they haven't been successfully imported
    assertBatchActive(batch0);
  }

  @Test
  void shouldRemoveBatchFromActiveSetWhenImportCompletesSuccessfully() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());

    assertBatchImported(batch0);

    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);

    assertBatchNotActive(batch0);
  }

  @Test
  void shouldSwitchChains() {
    // Start sync to first chain
    final SafeFuture<SyncResult> firstSyncResult = sync.syncToChain(targetChain);

    assertThat(batches).hasSize(5);
    final Batch batch0 = batches.get(0);
    final Batch batch4 = batches.get(4);

    targetChain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(2000), dataStructureUtil.randomBytes32()),
            syncSource);
    final SafeFuture<SyncResult> secondSyncResult = sync.syncToChain(targetChain);
    assertThat(firstSyncResult).isCompletedWithValue(SyncResult.TARGET_CHANGED);

    // It should optimistically assume the new chain is an extension of the old one and just keep
    // adding batches of block to the end
    batches.receiveBlocks(batch0);

    assertThat(batches).hasSize(6);
    final Batch batch5 = batches.get(5);
    assertThatBatch(batch5).hasFirstSlot(batch4.getLastSlot().plus(1));
    assertThat(secondSyncResult).isNotDone();
  }

  @Test
  void shouldRestartSyncFromCommonAncestorWhenBatchFromNewChainDoesNotLineUp() {
    // Start sync to first chain
    final SafeFuture<SyncResult> firstSyncResult = sync.syncToChain(targetChain);

    assertThat(batches).hasSize(5);
    final Batch batch0 = batches.get(0);
    final Batch batch4 = batches.get(4);

    targetChain =
        chainWith(
            new SlotAndBlockRoot(UInt64.valueOf(2000), dataStructureUtil.randomBytes32()),
            syncSource);
    final SafeFuture<SyncResult> secondSyncResult = sync.syncToChain(targetChain);
    assertThat(firstSyncResult).isCompletedWithValue(SyncResult.TARGET_CHANGED);

    // It should optimistically assume the new chain is an extension of the old one and just keep
    // adding batches of block to the end
    batches.receiveBlocks(batch0);

    assertThat(batches).hasSize(6);
    final Batch batch5 = batches.get(5);
    assertThatBatch(batch5).hasFirstSlot(batch4.getLastSlot().plus(1));

    // We get the last block we requested from the original chain
    batches.receiveBlocks(batch4, dataStructureUtil.randomSignedBeaconBlock(batch4.getLastSlot()));

    // The sync is going to recreate all the early batches so clear out list to make it easier
    // to keep track
    final List<Batch> originalBatches = batches.clearBatchList();

    // Setup an expected common ancestor to start syncing the new chain from
    final SafeFuture<UInt64> commonAncestorFuture = new SafeFuture<>();
    when(commonAncestor.findCommonAncestor(targetChain)).thenReturn(commonAncestorFuture);

    // And then get the first block of the new chain which doesn't line up
    // So we now know the new chain doesn't extend the old one
    batches.receiveBlocks(batch5, dataStructureUtil.randomSignedBeaconBlock(batch5.getFirstSlot()));

    // We should not apply any penalties because peers didn't claim it was the same chain
    originalBatches.forEach(
        batch -> {
          assertThatBatch(batch).isNotContested();
          batches.assertNotMarkedInvalid(batch);
        });

    // Should try to find the common ancestor with the new chain and not create any batches yet
    verify(commonAncestor).findCommonAncestor(targetChain);
    assertThat(batches).hasSize(0);

    // Then the common ancestor is found
    final UInt64 commonAncestorSlot = UInt64.valueOf(70);
    commonAncestorFuture.complete(commonAncestorSlot);

    // Should have recreated the batches from finalized epoch again
    assertThat(batches).hasSize(5);
    assertThatBatch(batches.get(0)).hasFirstSlot(commonAncestorSlot.plus(1));
    assertThat(secondSyncResult).isNotDone();
  }

  @Test
  void shouldImportNextConfirmedBatchWhenFirstBatchImportCompletes() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    final Batch batch2 = batches.get(2);
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());
    batches.receiveBlocks(
        batch2, chainBuilder.generateBlockAtSlot(batch2.getFirstSlot()).getBlock());

    assertThatBatch(batch0).isConfirmed();
    assertThatBatch(batch1).isConfirmed();
    assertBatchImported(batch0);

    // Batch 1 doesn't start importing until batch 0 completes
    batches.getImportResult(batch0).complete(IMPORTED_ALL_BLOCKS);
    assertBatchImported(batch1);
  }

  @Test
  void shouldDelaySwitchingToNewChainUntilCurrentImportCompletes() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final Batch batch0 = batches.get(0);
    final Batch batch1 = batches.get(1);
    batches.receiveBlocks(batch0, chainBuilder.generateBlockAtSlot(1).getBlock());
    batches.receiveBlocks(
        batch1, chainBuilder.generateBlockAtSlot(batch1.getFirstSlot()).getBlock());

    assertBatchImported(batch0);

    final Batch batch4 = batches.get(4);

    // Switch to a new chain
    targetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot(), syncSource);
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    // And return blocks so the new chain doesn't match up.
    batches.receiveBlocks(
        batch4, chainBuilder.generateBlockAtSlot(batch4.getLastSlot()).getBlock());
    final Batch batch5 = batches.get(5);
    batches.receiveBlocks(batch5, dataStructureUtil.randomSignedBeaconBlock(batch5.getFirstSlot()));

    assertBatchNotActive(batch0);

    // All batches should have been dropped and none started until the import completes
    batches.forEach(this::assertBatchNotActive);
    batches.clearBatchList();

    final SignedBlockAndState finalizedBlock = storageSystem.chainUpdater().finalizeEpoch(1);
    batches.getImportResult(batch0).complete(IMPORT_FAILED);

    // Now we should start downloading from the latest finalized checkpoint
    assertThat(batches.get(0).getFirstSlot()).isEqualTo(finalizedBlock.getSlot());
  }

  @Test
  void shouldProgressWhenThereAreManyEmptyBatchesInARow() {
    assertThat(sync.syncToChain(targetChain)).isNotDone();

    final int initialBatchCount = batches.size();
    // All the requested batches are empty
    for (int i = 0; i < initialBatchCount; i++) {
      batches.receiveBlocks(batches.get(i));
    }

    // But because they're all empty, it should request more batches
    assertThat(batches).hasSizeGreaterThan(initialBatchCount);

    // And then we get one back with a valid block
    final Batch laterBatch = batches.get(initialBatchCount);
    batches.receiveBlocks(
        laterBatch, chainBuilder.generateBlockAtSlot(laterBatch.getFirstSlot()).getBlock());

    // But nothing gets imported yet because it isn't confirmed.
    verifyNoInteractions(batchImporter);
    assertBatchActive(batches.get(0));

    // Finally it's confirmed
    final Batch confirmingBatch = batches.get(initialBatchCount + 1);
    batches.receiveBlocks(
        confirmingBatch,
        chainBuilder.generateBlockAtSlot(confirmingBatch.getFirstSlot()).getBlock());

    // So all the batches get imported, but because there's no point importing empty batches
    // only laterBatch is passed to the BatchImporter
    assertBatchImported(laterBatch);

    // And when it completes it and all the earlier empty batches are dropped
    batches.getImportResult(laterBatch).complete(IMPORTED_ALL_BLOCKS);
    for (int i = 0; i <= initialBatchCount; i++) {
      assertBatchNotActive(batches.get(i));
    }
  }

  private void assertBatchNotActive(final Batch batch) {
    // Need to use the wrapped batch which enforces usage of event thread
    eventThread.execute(
        () -> assertThat(sync.isActiveBatch(batches.getEventThreadOnlyBatch(batch))).isFalse());
  }

  private void assertBatchActive(final Batch batch) {
    // Need to use the wrapped batch which enforces usage of event thread
    eventThread.execute(
        () -> assertThat(sync.isActiveBatch(batches.getEventThreadOnlyBatch(batch))).isTrue());
  }

  private void assertBatchImported(final Batch batch) {
    verify(batchImporter).importBatch(batches.getEventThreadOnlyBatch(batch));
    verifyNoMoreInteractions(batchImporter);
  }

  private void assertNoBatchesImported() {
    verifyNoInteractions(batchImporter);
  }
}
