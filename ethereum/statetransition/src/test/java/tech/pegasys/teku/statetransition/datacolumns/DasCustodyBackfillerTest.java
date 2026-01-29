/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

class DasCustodyBackfillerTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final DataColumnSidecarCustody dataColumnSidecarCustody =
      mock(DataColumnSidecarCustody.class);
  private final CustodyGroupCountManager custodyGroupCountManager =
      mock(CustodyGroupCountManager.class);
  private final DataColumnSidecarRetriever retriever = mock(DataColumnSidecarRetriever.class);
  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator =
      mock(MinCustodyPeriodSlotCalculator.class);

  // Cursor management mocks
  private final AtomicReference<Optional<UInt64>> earliestAvailableColumnSlotStore =
      new AtomicReference<>(Optional.empty());
  private final Supplier<SafeFuture<Optional<UInt64>>> earliestAvailableColumnSlotProvider =
      () -> completedFuture(earliestAvailableColumnSlotStore.get());
  private final Function<UInt64, SafeFuture<Void>> earliestAvailableColumnSlotWriter =
      (val) -> {
        earliestAvailableColumnSlotStore.set(Optional.of(val));
        return SafeFuture.COMPLETE;
      };

  private DasCustodyBackfiller backfiller;

  private static final int BATCH_SIZE = 10;
  private static final int SYNCED_CUSTODY_GROUP_COUNT = 1;
  private static final int CUSTODY_GROUP_COUNT = 2;
  private static final List<UInt64> CUSTODY_INDICES = List.of(UInt64.ZERO, UInt64.ONE);

  @BeforeEach
  void setUp() {
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(recentChainData.getSpec()).thenReturn(spec);
    when(custodyGroupCountManager.getCustodyGroupSyncedCount())
        .thenReturn(SYNCED_CUSTODY_GROUP_COUNT);
    when(custodyGroupCountManager.getCustodyGroupCount()).thenReturn(CUSTODY_GROUP_COUNT);
    when(custodyGroupCountManager.getCustodyColumnIndices()).thenReturn(CUSTODY_INDICES);

    // Default: Not finalized yet, or finalized far back
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(UInt64.ZERO));
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(UInt64.valueOf(1000));

    // Default min custody slot is very old
    when(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(any()))
        .thenReturn(Optional.of(UInt64.ZERO));

    backfiller =
        new DasCustodyBackfiller(
            combinedChainDataClient,
            Duration.ofSeconds(1),
            dataColumnSidecarCustody,
            custodyGroupCountManager,
            retriever,
            minCustodyPeriodSlotCalculator,
            asyncRunner,
            earliestAvailableColumnSlotProvider,
            earliestAvailableColumnSlotWriter,
            BATCH_SIZE);

    backfiller.setFirstRoundAfterStartup(false);
    backfiller.onNodeSyncStateChanged(true);
  }

  @Test
  void start_shouldScheduleBackfillTask() {
    safeJoin(backfiller.start());
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
  }

  @Test
  void stop_shouldCancelScheduledTask() {
    safeJoin(backfiller.start());
    safeJoin(backfiller.stop());

    asyncRunner.executeQueuedActions();
    verifyNoInteractions(retriever);
    verifyNoInteractions(combinedChainDataClient);
    verifyNoInteractions(recentChainData);
  }

  @Test
  void onGroupCountUpdate_shouldTriggerResyncWhenCountIncreases() {
    // Setup initial state
    earliestAvailableColumnSlotStore.set(Optional.of(UInt64.valueOf(500)));
    safeJoin(backfiller.start());

    // Increase custody count
    backfiller.onGroupCountUpdate(CUSTODY_GROUP_COUNT, 32);

    // Run the triggered task
    asyncRunner.executeQueuedActions();

    // Should have reset the cursor to current slot (1000)
    assertThat(earliestAvailableColumnSlotStore.get()).isPresent().hasValue(UInt64.valueOf(1000));
  }

  @Test
  void onGroupCountUpdate_shouldIgnoreIfCountDoesNotIncrease() {
    // Setup initial state
    earliestAvailableColumnSlotStore.set(Optional.of(UInt64.valueOf(500)));

    safeJoin(backfiller.start());

    backfiller.onGroupCountUpdate(SYNCED_CUSTODY_GROUP_COUNT, 32); // Same custody count

    // stub requests
    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of()));
    when(combinedChainDataClient.getBlockInEffectAtSlot(any()))
        .thenReturn(completedFuture(Optional.empty()));

    // run again
    asyncRunner.executeQueuedActions();

    // Should not trigger logic to reset cursor
    assertThat(earliestAvailableColumnSlotStore.get()).isPresent().hasValue(UInt64.valueOf(500));
  }

  @Test
  void shouldRunHeadsCustodyCheckAtFirstRound() {
    earliestAvailableColumnSlotStore.set(Optional.of(UInt64.valueOf(90)));
    final UInt64 head1Slot = UInt64.valueOf(200);
    final UInt64 head2Slot = UInt64.valueOf(202);

    ProtoNodeData head1 = mock(ProtoNodeData.class);
    when(head1.getSlot()).thenReturn(head1Slot);
    ProtoNodeData head2 = mock(ProtoNodeData.class);
    when(head2.getSlot()).thenReturn(head2Slot);
    when(recentChainData.getChainHeads()).thenReturn(List.of(head1, head2));

    backfiller.setFirstRoundAfterStartup(true);

    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(head1Slot, 1);
    final SignedBeaconBlock block2 =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(head2Slot, 1);

    when(recentChainData.getAllBlockRootsAtSlot(head1Slot)).thenReturn(List.of(block1.getRoot()));
    when(recentChainData.getAllBlockRootsAtSlot(head2Slot)).thenReturn(List.of(block2.getRoot()));

    when(recentChainData.retrieveSignedBlockByRoot(block1.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block1)));
    when(recentChainData.retrieveSignedBlockByRoot(block2.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block2)));

    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of()));

    when(retriever.retrieve(any())).thenReturn(completedFuture(mock(DataColumnSidecar.class)));
    when(dataColumnSidecarCustody.onNewValidatedDataColumnSidecar(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    // We expect a batch to be created from most recent head
    verify(combinedChainDataClient)
        .getDataColumnIdentifiers(eq(UInt64.valueOf(193)), eq(UInt64.valueOf(202)), any());

    // we expect 4 columns to be retrieved and stored
    verify(retriever, times(4)).retrieve(any());
    verify(dataColumnSidecarCustody, times(4)).onNewValidatedDataColumnSidecar(any(), any());

    // no change in earliest available slot
    assertThat(earliestAvailableColumnSlotStore.get()).contains(UInt64.valueOf(90));
  }

  @Test
  void shouldInitializeEarliestAvailableColumnSlotIfEmpty() {
    earliestAvailableColumnSlotStore.set(Optional.empty());

    // Setup chain heads to determine start point
    ProtoNodeData head = mock(ProtoNodeData.class);
    when(head.getSlot()).thenReturn(UInt64.valueOf(200));
    when(recentChainData.getChainHeads()).thenReturn(List.of(head));

    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    // Cursor should be head + 1 => 201
    assertThat(earliestAvailableColumnSlotStore.get()).isPresent().hasValue(UInt64.valueOf(201));
  }

  @Test
  void shouldDoNothingIfSyncing() {
    // Setup Range: [91, 100] (Batch size 10)
    final UInt64 startingEarliestAvailableColumnSlot = UInt64.valueOf(101);
    earliestAvailableColumnSlotStore.set(Optional.of(startingEarliestAvailableColumnSlot));

    backfiller.onNodeSyncStateChanged(false);

    // Run
    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    verifyNoInteractions(retriever);
    verifyNoInteractions(dataColumnSidecarCustody);

    assertThat(earliestAvailableColumnSlotStore.get())
        .isPresent()
        .hasValue(startingEarliestAvailableColumnSlot);
  }

  @Test
  void shouldRequestMissingColumnsForBlockWithBlobs() {
    // Setup Range: [91, 100] (Batch size 10)

    earliestAvailableColumnSlotStore.set(Optional.of(UInt64.valueOf(101)));

    // Prepare one finalized block at slot 95 and 2 hot blocks at slot 98

    final UInt64 finalizedBlockSlot = UInt64.valueOf(95);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(finalizedBlockSlot, 1);
    final Bytes32 finalizedBlockRoot = block.getRoot();

    final UInt64 hotBlocksSlot = UInt64.valueOf(98);
    final SignedBeaconBlock hotBlock1 =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(hotBlocksSlot, 1);
    final Bytes32 hotBlock1Root = hotBlock1.getRoot();
    final SignedBeaconBlock hotBlock2 =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(hotBlocksSlot, 1);
    final Bytes32 hotBlock2Root = hotBlock2.getRoot();

    // DB knows about finalized and hot blocks
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(finalizedBlockSlot));
    when(combinedChainDataClient.isFinalized(finalizedBlockSlot)).thenReturn(true);
    when(combinedChainDataClient.isFinalized(hotBlocksSlot)).thenReturn(false);
    when(combinedChainDataClient.getFinalizedBlockAtSlotExact(finalizedBlockSlot))
        .thenReturn(completedFuture(Optional.of(block)));
    when(recentChainData.getAllBlockRootsAtSlot(hotBlocksSlot))
        .thenReturn(List.of(hotBlock1Root, hotBlock2Root));
    when(recentChainData.retrieveSignedBlockByRoot(hotBlock1Root))
        .thenReturn(SafeFuture.completedFuture(Optional.of(hotBlock1)));
    when(recentChainData.retrieveSignedBlockByRoot(hotBlock2Root))
        .thenReturn(SafeFuture.completedFuture(Optional.of(hotBlock2)));

    final DataColumnSlotAndIdentifier existingFinalizedCol =
        new DataColumnSlotAndIdentifier(finalizedBlockSlot, finalizedBlockRoot, UInt64.ZERO);
    final DataColumnSlotAndIdentifier existingHot1Col =
        new DataColumnSlotAndIdentifier(hotBlocksSlot, hotBlock1Root, UInt64.ONE);

    // index 1 missing for finalized
    // index 0 missing for hot 1
    // index 0 and 1 missing for hot 2
    when(combinedChainDataClient.getDataColumnIdentifiers(
            eq(UInt64.valueOf(91)), eq(UInt64.valueOf(100)), any()))
        .thenReturn(completedFuture(List.of(existingFinalizedCol, existingHot1Col)));

    // Mock retrieval
    SafeFuture<DataColumnSidecar> retrievalFuture = completedFuture(mock(DataColumnSidecar.class));
    when(retriever.retrieve(any())).thenReturn(retrievalFuture);
    when(dataColumnSidecarCustody.onNewValidatedDataColumnSidecar(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    // Run
    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    // Verify we requested the missing column
    DataColumnSlotAndIdentifier expectedFinalizedMissing =
        new DataColumnSlotAndIdentifier(finalizedBlockSlot, finalizedBlockRoot, UInt64.ONE);
    DataColumnSlotAndIdentifier expectedHot1Missing =
        new DataColumnSlotAndIdentifier(hotBlocksSlot, hotBlock1Root, UInt64.ZERO);
    DataColumnSlotAndIdentifier expectedHot2Missing1 =
        new DataColumnSlotAndIdentifier(hotBlocksSlot, hotBlock2Root, UInt64.ZERO);
    DataColumnSlotAndIdentifier expectedHot2Missing2 =
        new DataColumnSlotAndIdentifier(hotBlocksSlot, hotBlock2Root, UInt64.ONE);

    verify(retriever).retrieve(expectedFinalizedMissing);
    verify(retriever).retrieve(expectedHot1Missing);
    verify(retriever).retrieve(expectedHot2Missing1);
    verify(retriever).retrieve(expectedHot2Missing2);
    verify(dataColumnSidecarCustody, times(4))
        .onNewValidatedDataColumnSidecar(any(), eq(RemoteOrigin.RPC));

    // Verify cursor update (moves to the block slot)
    assertThat(earliestAvailableColumnSlotStore.get()).isPresent().hasValue(finalizedBlockSlot);
  }

  @Test
  void shouldSkipBlocksWithoutBlobs() {
    // Setup Range: [91, 100] (Batch size 10)

    earliestAvailableColumnSlotStore.set(Optional.of(UInt64.valueOf(101)));

    UInt64 blockSlot = UInt64.valueOf(95);
    SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(blockSlot, 0);

    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(UInt64.MAX_VALUE));
    when(combinedChainDataClient.isFinalized(blockSlot)).thenReturn(true);
    when(combinedChainDataClient.getFinalizedBlockAtSlotExact(blockSlot))
        .thenReturn(completedFuture(Optional.of(block)));

    // DB has no columns
    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of()));

    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    // Verify NO retrieval attempted because block has no blobs
    verify(retriever, never()).retrieve(any());

    // Cursor should move past the block (to 95)
    assertThat(earliestAvailableColumnSlotStore.get()).isPresent().hasValue(blockSlot);
  }

  @Test
  void shouldHandleGapsWhenNoBlocksInBatch() {
    // Range [91, 100], but no blocks exist in DB for this range

    earliestAvailableColumnSlotStore.set(Optional.of(UInt64.valueOf(101)));

    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(UInt64.MAX_VALUE));
    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of()));

    // Return empty for block lookups
    when(combinedChainDataClient.getFinalizedBlockAtSlotExact(any()))
        .thenReturn(completedFuture(Optional.empty()));

    // Mock finding the previous block to jump the gap
    UInt64 olderBlockSlot = UInt64.valueOf(80);
    SignedBlockAndState olderBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(olderBlockSlot);
    when(combinedChainDataClient.getBlockInEffectAtSlot(eq(UInt64.valueOf(90))))
        .thenReturn(completedFuture(Optional.of(olderBlockAndState.getBlock())));

    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    // Cursor should jump to olderBlockSlot + 1 -> 81
    assertThat(earliestAvailableColumnSlotStore.get()).isPresent().hasValue(UInt64.valueOf(81));
  }

  @Test
  void onNewFinalizedCheckpoint_shouldCancelRequestsForNonCanonicalBlocks() {
    // 1. Setup initial state

    earliestAvailableColumnSlotStore.set(Optional.of(UInt64.valueOf(101)));

    // Override custody settings for this test to only require 1 column (Index 0).
    // This ensures 'retrieve' is called exactly ONCE per block, matching the verify expectation.
    when(custodyGroupCountManager.getCustodyColumnIndices()).thenReturn(List.of(UInt64.ZERO));

    final UInt64 blockSlot = UInt64.valueOf(95);
    final SignedBeaconBlock canonicalBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(blockSlot, 1);
    final SignedBeaconBlock forkBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(blockSlot, 1);

    // Must have a finalized slot to proceed, but it must be older than our blocks
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(UInt64.ZERO));

    // The blocks at 95 are NOT finalized
    when(combinedChainDataClient.isFinalized(any())).thenReturn(false);

    // Return both blocks at the slot
    when(recentChainData.getAllBlockRootsAtSlot(blockSlot))
        .thenReturn(List.of(canonicalBlock.getRoot(), forkBlock.getRoot()));

    when(recentChainData.retrieveSignedBlockByRoot(canonicalBlock.getRoot()))
        .thenReturn(completedFuture(Optional.of(canonicalBlock)));
    when(recentChainData.retrieveSignedBlockByRoot(forkBlock.getRoot()))
        .thenReturn(completedFuture(Optional.of(forkBlock)));

    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of()));

    // Stub retrieval to hang so requests stay pending
    final SafeFuture<DataColumnSidecar> pendingFutureCanonical = new SafeFuture<>();
    final SafeFuture<DataColumnSidecar> pendingFutureFork = new SafeFuture<>();

    // argThatMatch ignores column index, so this stub applies to any column request for that block
    when(retriever.retrieve(columnIdArgThatMatch(blockSlot, canonicalBlock.getRoot())))
        .thenReturn(pendingFutureCanonical);
    when(retriever.retrieve(columnIdArgThatMatch(blockSlot, forkBlock.getRoot())))
        .thenReturn(pendingFutureFork);

    // Start backfill
    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    // Verify both were requested (Now strictly 1 time each because we restricted custody to [0])
    verify(retriever).retrieve(columnIdArgThatMatch(blockSlot, canonicalBlock.getRoot()));
    verify(retriever).retrieve(columnIdArgThatMatch(blockSlot, forkBlock.getRoot()));

    // 2. Setup Finalization triggers

    // Helper to return the slot for the canonical root, and empty for the fork
    when(combinedChainDataClient.getFinalizedSlotByBlockRoot(canonicalBlock.getRoot()))
        .thenReturn(completedFuture(Optional.of(blockSlot)));
    when(combinedChainDataClient.getFinalizedSlotByBlockRoot(forkBlock.getRoot()))
        .thenReturn(completedFuture(Optional.empty())); // Orphaned

    // Trigger cleanup.
    // First call sets the 'lastFinalizedCheckpoint' reference (acting as 'from' slot)
    backfiller.onNewFinalizedCheckpoint(dataStructureUtil.randomCheckpoint(0), false);

    // Second call provides a range that includes slot 95.
    // Epoch 20 -> Start Slot > 95.
    final Checkpoint newCheckpoint = dataStructureUtil.randomCheckpoint(20);
    backfiller.onNewFinalizedCheckpoint(newCheckpoint, false);

    // 3. Verify Cancellation
    assertThat(pendingFutureCanonical.isCancelled()).isFalse();
    assertThat(pendingFutureFork.isCancelled()).isTrue();
  }

  @Test
  void shouldCompleteAndNotifyManagerWhenReachingMinCustodySlot() {
    final UInt64 minCustodySlot = UInt64.valueOf(100);

    earliestAvailableColumnSlotStore.set(Optional.of(UInt64.valueOf(101)));

    when(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(any()))
        .thenReturn(Optional.of(minCustodySlot));

    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(minCustodySlot, 1);
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(UInt64.MAX_VALUE));
    when(combinedChainDataClient.isFinalized(minCustodySlot)).thenReturn(true);
    when(combinedChainDataClient.getFinalizedBlockAtSlotExact(minCustodySlot))
        .thenReturn(completedFuture(Optional.of(block)));

    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of()));

    when(retriever.retrieve(any())).thenReturn(completedFuture(mock(DataColumnSidecar.class)));
    when(dataColumnSidecarCustody.onNewValidatedDataColumnSidecar(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    assertThat(earliestAvailableColumnSlotStore.get()).isPresent().hasValue(minCustodySlot);
    verify(custodyGroupCountManager).setCustodyGroupSyncedCount(CUSTODY_GROUP_COUNT);
  }

  @Test
  void shouldNotRunIfCursorIsAlreadyAtMinCustodySlot() {
    final UInt64 minCustodySlot = UInt64.valueOf(100);
    earliestAvailableColumnSlotStore.set(Optional.of(minCustodySlot));

    when(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(any()))
        .thenReturn(Optional.of(minCustodySlot));

    safeJoin(backfiller.start());
    asyncRunner.executeQueuedActions();

    // Should return false immediately, triggering no DB lookups
    verify(combinedChainDataClient, never()).getDataColumnIdentifiers(any(), any(), any());
    verify(combinedChainDataClient, never()).getFinalizedBlockAtSlotExact(any());

    // Cursor remains unchanged
    assertThat(earliestAvailableColumnSlotStore.get()).isPresent().hasValue(minCustodySlot);
  }

  // --- Helper Methods ---

  private DataColumnSlotAndIdentifier columnIdArgThatMatch(final UInt64 slot, final Bytes32 root) {
    return argThat(
        argument ->
            argument != null && argument.slot().equals(slot) && argument.blockRoot().equals(root));
  }
}
