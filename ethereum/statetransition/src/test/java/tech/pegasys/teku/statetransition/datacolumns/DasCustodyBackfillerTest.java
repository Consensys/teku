/*
 * Copyright Consensys Software Inc., 2025
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

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
  private final AtomicReference<Optional<UInt64>> cursorStore =
      new AtomicReference<>(Optional.empty());
  private final Supplier<SafeFuture<Optional<UInt64>>> cursorProvider =
      () -> completedFuture(cursorStore.get());
  private final Function<UInt64, SafeFuture<Void>> cursorWriter =
      (val) -> {
        cursorStore.set(Optional.of(val));
        return SafeFuture.COMPLETE;
      };

  private DasCustodyBackfiller backfiller;

  private static final int BATCH_SIZE = 10;
  private static final int CUSTODY_GROUP_COUNT = 4;
  private static final List<UInt64> CUSTODY_INDICES = List.of(UInt64.ZERO, UInt64.ONE);

  @BeforeEach
  void setUp() {
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(recentChainData.getSpec()).thenReturn(spec);
    when(custodyGroupCountManager.getCustodyGroupSyncedCount()).thenReturn(CUSTODY_GROUP_COUNT);
    when(custodyGroupCountManager.getCustodyColumnIndices()).thenReturn(CUSTODY_INDICES);

    // Default: Not finalized yet, or finalized far back
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(UInt64.ZERO));
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(UInt64.valueOf(1000));

    // Default min custody slot is very old
    when(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(any())).thenReturn(Optional.of(UInt64.ZERO));

    backfiller =
        new DasCustodyBackfiller(
            combinedChainDataClient,
            Duration.ofSeconds(1),
            dataColumnSidecarCustody,
            custodyGroupCountManager,
            retriever,
            minCustodyPeriodSlotCalculator,
            asyncRunner,
            cursorProvider,
            cursorWriter,
            BATCH_SIZE);
  }

  @Test
  void start_shouldScheduleBackfillTask() {
    backfiller.start();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
  }

  @Test
  void stop_shouldCancelScheduledTask() {
    backfiller.start();
    backfiller.stop();
    // StubAsyncRunner doesn't remove cancelled tasks from the queue immediately,
    // but we can verify no interactions happen if we execute queued actions
    asyncRunner.executeQueuedActions();
    verifyNoInteractions(retriever);
  }

  @Test
  void onGroupCountUpdate_shouldTriggerResyncWhenCountIncreases() {
    // Setup initial state
    cursorStore.set(Optional.of(UInt64.valueOf(500)));
    backfiller.start();

    // Increase custody count
    backfiller.onGroupCountUpdate(CUSTODY_GROUP_COUNT + 1, 32);

    // Run the triggered task
    asyncRunner.executeQueuedActions();

    // Should have reset the cursor to current slot (1000)
    assertThat(cursorStore.get()).isPresent().hasValue(UInt64.valueOf(1000));

    // Should verify re-scheduling
    assertThat(asyncRunner.countDelayedActions()).isGreaterThan(0);
  }

  @Test
  void onGroupCountUpdate_shouldIgnorIfCountDoesNotIncrease() {
    backfiller.start();
    asyncRunner.executeQueuedActions(); // Run initial check

    backfiller.onGroupCountUpdate(CUSTODY_GROUP_COUNT, 32); // Same count

    // Should not trigger logic to reset cursor
    verify(combinedChainDataClient, never()).getCurrentSlot();
  }

  @Test
  void backfill_shouldInitializeCursorIfEmpty() {
    cursorStore.set(Optional.empty());

    // Setup chain heads to determine start point
    ProtoNodeData head = mock(ProtoNodeData.class);
    when(head.getSlot()).thenReturn(UInt64.valueOf(200));
    when(recentChainData.getChainHeads()).thenReturn(List.of(head));

    backfiller.start();
    asyncRunner.executeQueuedActions();

    // Cursor should be head + 1 => 201
    assertThat(cursorStore.get()).isPresent().hasValue(UInt64.valueOf(201));
  }

  @Test
  void backfill_shouldRequestMissingColumnsForBlockWithBlobs() {
    // Setup Range: [91, 100] (Batch size 10)
    UInt64 startCursor = UInt64.valueOf(101); // So latest in batch is 100
    cursorStore.set(Optional.of(startCursor));

    UInt64 blockSlot = UInt64.valueOf(95);
    SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(blockSlot, 1);
    Bytes32 blockRoot = block.getRoot();

    // DB knows about the block
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(UInt64.MAX_VALUE)); // Ensure finalized
    when(combinedChainDataClient.isFinalized(blockSlot)).thenReturn(true);
    when(combinedChainDataClient.getFinalizedBlockAtSlotExact(blockSlot))
        .thenReturn(completedFuture(Optional.of(block)));

    // DB only has column index 0. We need [0, 1]. So Index 1 is missing.
    DataColumnSlotAndIdentifier existingCol =
        new DataColumnSlotAndIdentifier(blockSlot, blockRoot, UInt64.ZERO);
    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of(existingCol)));

    // Mock retrieval
    SafeFuture<DataColumnSidecar> retrievalFuture = completedFuture(mock(DataColumnSidecar.class));
    when(retriever.retrieve(any())).thenReturn(retrievalFuture);
    when(dataColumnSidecarCustody.onNewValidatedDataColumnSidecar(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    // Run
    backfiller.start();
    asyncRunner.executeQueuedActions();

    // Verify we requested the missing column (Index 1)
    DataColumnSlotAndIdentifier expectedMissing =
        new DataColumnSlotAndIdentifier(blockSlot, blockRoot, UInt64.ONE);
    verify(retriever).retrieve(expectedMissing);
    verify(dataColumnSidecarCustody).onNewValidatedDataColumnSidecar(any(), eq(RemoteOrigin.RPC));

    // Verify cursor update (moves to the block slot)
    assertThat(cursorStore.get()).isPresent().hasValue(blockSlot);
  }

  @Test
  void backfill_shouldSkipBlocksWithoutBlobs() {
    UInt64 startCursor = UInt64.valueOf(101);
    cursorStore.set(Optional.of(startCursor));

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

    backfiller.start();
    asyncRunner.executeQueuedActions();

    // Verify NO retrieval attempted because block has no blobs
    verify(retriever, never()).retrieve(any());

    // Cursor should move past the block (to 95)
    assertThat(cursorStore.get()).isPresent().hasValue(blockSlot);
  }

  @Test
  void backfill_shouldHandleGapsWhenNoBlocksInBatch() {
    // Range [91, 100], but no blocks exist in DB for this range
    UInt64 startCursor = UInt64.valueOf(101);
    cursorStore.set(Optional.of(startCursor));

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

    backfiller.start();
    asyncRunner.executeQueuedActions();

    // Cursor should jump to olderBlockSlot + 1 -> 81
    assertThat(cursorStore.get()).isPresent().hasValue(UInt64.valueOf(81));
  }

  @Test
  void onNewFinalizedCheckpoint_shouldCancelRequestsForNonCanonicalBlocks() {
    // 1. Setup initial state
    UInt64 startCursor = UInt64.valueOf(101);
    cursorStore.set(Optional.of(startCursor));

    // Override custody settings for this test to only require 1 column (Index 0).
    // This ensures 'retrieve' is called exactly ONCE per block, matching the verify expectation.
    when(custodyGroupCountManager.getCustodyColumnIndices()).thenReturn(List.of(UInt64.ZERO));

    UInt64 blockSlot = UInt64.valueOf(95);
    SignedBeaconBlock canonicalBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(blockSlot, 1);
    SignedBeaconBlock forkBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(blockSlot, 1);

    // FIX: Must have a finalized slot to proceed, but it must be older than our blocks
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(UInt64.ZERO));

    // The blocks at 95 are NOT finalized
    when(combinedChainDataClient.isFinalized(any())).thenReturn(false);

    // Return both blocks at the slot
    when(recentChainData.getAllBlockRootsAtSlot(blockSlot))
        .thenReturn(List.of(canonicalBlock.getRoot(), forkBlock.getRoot())); // Use Set or List

    when(recentChainData.retrieveSignedBlockByRoot(canonicalBlock.getRoot()))
        .thenReturn(completedFuture(Optional.of(canonicalBlock)));
    when(recentChainData.retrieveSignedBlockByRoot(forkBlock.getRoot()))
        .thenReturn(completedFuture(Optional.of(forkBlock)));

    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of()));

    // Stub retrieval to hang so requests stay pending
    SafeFuture<DataColumnSidecar> pendingFutureCanonical = new SafeFuture<>();
    SafeFuture<DataColumnSidecar> pendingFutureFork = new SafeFuture<>();

    // argThatMatch ignores column index, so this stub applies to any column request for that block
    when(retriever.retrieve(argThatMatch(blockSlot, canonicalBlock.getRoot())))
        .thenReturn(pendingFutureCanonical);
    when(retriever.retrieve(argThatMatch(blockSlot, forkBlock.getRoot())))
        .thenReturn(pendingFutureFork);

    // Start backfill
    backfiller.start();
    asyncRunner.executeQueuedActions();

    // Verify both were requested (Now strictly 1 time each because we restricted custody to [0])
    verify(retriever).retrieve(argThatMatch(blockSlot, canonicalBlock.getRoot()));
    verify(retriever).retrieve(argThatMatch(blockSlot, forkBlock.getRoot()));

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
    Checkpoint newCheckpoint = dataStructureUtil.randomCheckpoint(20);
    backfiller.onNewFinalizedCheckpoint(newCheckpoint, false);

    // 3. Verify Cancellation
    assertThat(pendingFutureCanonical.isCancelled()).isFalse();
    assertThat(pendingFutureFork.isCancelled()).isTrue();
  }

  @Test
  void backfill_shouldCompleteAndNotifyManagerWhenReachingMinCustodySlot() {
    // Setup:
    // Min Custody Slot: 100
    // Current Cursor: 101
    // Batch will cover [100, 100]
    // Block at 100 exists
    UInt64 minCustodySlot = UInt64.valueOf(100);
    UInt64 startCursor = UInt64.valueOf(101);
    cursorStore.set(Optional.of(startCursor));

    when(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(any())).thenReturn(Optional.of(minCustodySlot));

    // Block at 100 exists and has blobs
    SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(minCustodySlot, 1);
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(UInt64.MAX_VALUE));
    when(combinedChainDataClient.isFinalized(minCustodySlot)).thenReturn(true);
    when(combinedChainDataClient.getFinalizedBlockAtSlotExact(minCustodySlot))
        .thenReturn(completedFuture(Optional.of(block)));

    // We are missing columns, so it does work
    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(completedFuture(List.of()));

    when(retriever.retrieve(any())).thenReturn(completedFuture(mock(DataColumnSidecar.class)));
    when(dataColumnSidecarCustody.onNewValidatedDataColumnSidecar(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    // Execute
    backfiller.start();
    asyncRunner.executeQueuedActions();

    // Verification:
    // 1. Cursor should be updated to 100
    assertThat(cursorStore.get()).isPresent().hasValue(minCustodySlot);

    // 2. IMPORTANT: Verify the manager was notified with the count of columns we are tracking
    verify(custodyGroupCountManager).setCustodyGroupSyncedCount(CUSTODY_INDICES.size());
  }

  @Test
  void backfill_shouldNotRunIfCursorIsAlreadyAtMinCustodySlot() {
    // Min Custody: 100, Cursor: 100
    UInt64 minCustodySlot = UInt64.valueOf(100);
    cursorStore.set(Optional.of(minCustodySlot));

    when(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(any())).thenReturn(Optional.of(minCustodySlot));

    backfiller.start();
    asyncRunner.executeQueuedActions();

    // Should return false immediately, triggering no DB lookups
    verify(combinedChainDataClient, never()).getDataColumnIdentifiers(any(), any(), any());
    verify(combinedChainDataClient, never()).getFinalizedBlockAtSlotExact(any());

    // Cursor remains unchanged
    assertThat(cursorStore.get()).isPresent().hasValue(minCustodySlot);
  }

  // --- Helper Methods ---

  private DataColumnSlotAndIdentifier argThatMatch(final UInt64 slot, final Bytes32 root) {
    return argThat(
        argument ->
            argument != null && argument.slot().equals(slot) && argument.blockRoot().equals(root));
  }
}
