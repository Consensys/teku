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

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler.SamplingEligibilityStatus;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DasSamplerBasicTest {
  private static final Spec SPEC = TestSpecFactory.createMinimalFulu();
  private static final List<UInt64> SAMPLING_INDICES =
      List.of(UInt64.valueOf(5), UInt64.ZERO, UInt64.valueOf(2));

  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(stubTimeProvider);
  private final RPCFetchDelayProvider rpcFetchDelayProvider = mock(RPCFetchDelayProvider.class);

  private RecentChainData recentChainData;
  private DataColumnSidecarCustody custody;
  private DataColumnSidecarRetriever retriever;
  private CurrentSlotProvider currentSlotProvider;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, SPEC);

  private DasSamplerBasic sampler;

  @BeforeEach
  public void setUp() {
    final CustodyGroupCountManager custodyGroupCountManager = mock(CustodyGroupCountManager.class);
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(SAMPLING_INDICES);
    recentChainData = mock(RecentChainData.class);
    custody = mock(DataColumnSidecarCustody.class);
    retriever = mock(DataColumnSidecarRetriever.class);
    currentSlotProvider = mock(CurrentSlotProvider.class);

    when(retriever.retrieve(any()))
        .thenReturn(SafeFuture.completedFuture(mock(DataColumnSidecar.class)));

    when(currentSlotProvider.getCurrentSlot()).thenReturn(UInt64.ZERO);
    when(custody.onNewValidatedDataColumnSidecar(any(), any())).thenReturn(SafeFuture.COMPLETE);

    sampler =
        new DasSamplerBasic(
            SPEC,
            asyncRunner,
            currentSlotProvider,
            rpcFetchDelayProvider,
            custody,
            retriever,
            custodyGroupCountManager,
            recentChainData,
            true);
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldAddToTracker() {
    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDICES.getFirst());

    final List<UInt64> remainingColumns = SAMPLING_INDICES.subList(1, SAMPLING_INDICES.size());

    sampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC);

    assertSamplerTracker(sidecar.getBeaconBlockRoot(), sidecar.getSlot(), remainingColumns);
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldScheduleRPCFetchWhenDelayIsNonZero() {
    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDICES.getFirst());

    final List<UInt64> remainingColumns = SAMPLING_INDICES.subList(1, SAMPLING_INDICES.size());

    when(rpcFetchDelayProvider.calculate(sidecar.getSlot())).thenReturn(Duration.ofSeconds(1));

    sampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC);

    assertSamplerTrackerHasRPCFetchScheduled(sidecar.getBeaconBlockRoot(), true);

    assertRPCFetchInMillis(
        sidecar.getSlot(), sidecar.getBeaconBlockRoot(), remainingColumns, 1_000);
    assertSamplerTrackerHasRPCFetchScheduled(sidecar.getBeaconBlockRoot(), false);
  }

  @Test
  void onNewValidatedDataColumnSidecar_shouldScheduleRPCFetchWhenDelayIsZero() {
    final DataColumnSidecar sidecar =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), SAMPLING_INDICES.getFirst());

    when(rpcFetchDelayProvider.calculate(sidecar.getSlot())).thenReturn(Duration.ZERO);

    sampler.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC);

    assertThat(asyncRunner.countDelayedActions()).isZero();
    assertSamplerTrackerHasRPCFetchScheduled(sidecar.getBeaconBlockRoot(), false);
  }

  @Test
  void checkDataAvailability_shouldAddToTrackerAndReturnCompletionFuture() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomSlot(), dataStructureUtil.randomBytes32());

    when(retriever.retrieve(any())).thenReturn(new SafeFuture<>());

    final SafeFuture<List<UInt64>> completionFuture =
        sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertSamplerTracker(
        slotAndBlockRoot.getBlockRoot(), slotAndBlockRoot.getSlot(), SAMPLING_INDICES);

    assertThat(
            sampler
                .getRecentlySampledColumnsByRoot()
                .get(slotAndBlockRoot.getBlockRoot())
                .completionFuture())
        .isSameAs(completionFuture);
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), true);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void checkDataAvailability_shouldCallRetrieverForMissingColumnsAndCallCustodyOnRetrieval() {
    final SignedBeaconBlockHeader block = dataStructureUtil.randomSignedBeaconBlockHeader();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(block.getMessage().getSlot(), block.getMessage().getRoot());

    final Map<UInt64, DataColumnSidecar> missingColumnSidecars =
        SAMPLING_INDICES.stream()
            .map(index -> dataStructureUtil.randomDataColumnSidecar(block, index))
            .collect(
                Collectors.toUnmodifiableMap(DataColumnSidecar::getIndex, Function.identity()));
    final Map<UInt64, SafeFuture<DataColumnSidecar>> futureSidecarsByIndex =
        missingColumnSidecars.values().stream()
            .map(DataColumnSidecar::getIndex)
            .map(index -> entry(index, new SafeFuture<DataColumnSidecar>()))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    final DataColumnSidecar lateArrivedSidecar =
        missingColumnSidecars.get(SAMPLING_INDICES.getFirst());
    final List<UInt64> remainingColumnsIndices =
        SAMPLING_INDICES.subList(1, SAMPLING_INDICES.size());

    // prepare retriever mock to return futures when called
    doAnswer(
            invocation -> {
              final DataColumnSlotAndIdentifier id = invocation.getArgument(0);
              return futureSidecarsByIndex.get(id.columnIndex());
            })
        .when(retriever)
        .retrieve(any());

    when(rpcFetchDelayProvider.calculate(slotAndBlockRoot.getSlot()))
        .thenReturn(Duration.ofSeconds(1));

    // da check is requested
    sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), true);

    assertRPCFetchInMillis(
        slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), SAMPLING_INDICES, 1_000);

    // we receive one sidecar late, while retriever is still working
    sampler.onNewValidatedDataColumnSidecar(lateArrivedSidecar, RemoteOrigin.GOSSIP);

    // let the retriever complete all the futures
    futureSidecarsByIndex.forEach(
        (index, future) -> future.complete(missingColumnSidecars.get(index)));

    // verify RPC fetch schedule is reset
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), false);

    // verify we never called custody for the late arrived sidecar
    verify(custody, never()).onNewValidatedDataColumnSidecar(eq(lateArrivedSidecar), any());

    // verify we call custody for all the retrieved sidecars
    for (final UInt64 missingColumn : remainingColumnsIndices) {
      verify(custody)
          .onNewValidatedDataColumnSidecar(
              missingColumnSidecars.get(missingColumn), RemoteOrigin.RPC);
    }

    verifyNoMoreInteractions(retriever);
    verifyNoMoreInteractions(custody);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void checkDataAvailability_shouldSetRPCFetchedToFalseOnFailureAndRPCFetchesAgain() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomSlot(), dataStructureUtil.randomBytes32());

    when(retriever.retrieve(any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("error")));

    final SafeFuture<List<UInt64>> completionFuture =
        sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertSamplerTracker(
        slotAndBlockRoot.getBlockRoot(), slotAndBlockRoot.getSlot(), SAMPLING_INDICES);

    assertThat(
            sampler
                .getRecentlySampledColumnsByRoot()
                .get(slotAndBlockRoot.getBlockRoot())
                .completionFuture())
        .isSameAs(completionFuture);
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), false);

    verify(retriever, times(SAMPLING_INDICES.size())).retrieve(any());

    when(retriever.retrieve(any())).thenReturn(new SafeFuture<>());

    final SafeFuture<List<UInt64>> secondCompletionFuture =
        sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertSamplerTracker(
        slotAndBlockRoot.getBlockRoot(), slotAndBlockRoot.getSlot(), SAMPLING_INDICES);

    verify(retriever, times(SAMPLING_INDICES.size() * 2)).retrieve(any());

    assertThat(secondCompletionFuture).isSameAs(completionFuture);
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), true);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void checkDataAvailability_shouldRPCFetchImmediatelyIfNotPreviouslyScheduled() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomSlot(), dataStructureUtil.randomBytes32());

    when(retriever.retrieve(any())).thenReturn(new SafeFuture<>());

    when(rpcFetchDelayProvider.calculate(slotAndBlockRoot.getSlot())).thenReturn(Duration.ZERO);

    sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    assertRPCFetchInMillis(
        slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), SAMPLING_INDICES, 0);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldHandleMultipleTrackersFromMultipleEntrypoints() {
    final DataColumnSlotAndIdentifier source1 =
        new DataColumnSlotAndIdentifier(
            dataStructureUtil.randomSlot(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64());
    final DataColumnSidecar source2 =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), dataStructureUtil.randomUInt64());
    final SlotAndBlockRoot source3 =
        new SlotAndBlockRoot(dataStructureUtil.randomSlot(), dataStructureUtil.randomBytes32());

    sampler.onNewValidatedDataColumnSidecar(source1, RemoteOrigin.CUSTODY);
    sampler.onNewValidatedDataColumnSidecar(source2, RemoteOrigin.RPC);
    sampler.checkDataAvailability(source3.getSlot(), source3.getBlockRoot());

    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .containsKey(source1.blockRoot())
        .containsKey(source2.getBeaconBlockRoot())
        .containsKey(source3.getBlockRoot());
  }

  @Test
  void flush_shouldForwardToRetriever() {
    sampler.flush();
    verify(retriever).flush();
  }

  @Test
  void checkSamplingEligibility_shouldReturnRequired() {

    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ZERO, 1);

    final SamplingEligibilityStatus result =
        sampler.checkSamplingEligibility(block.getBeaconBlock().orElseThrow());

    assertEquals(SamplingEligibilityStatus.REQUIRED, result);
  }

  @Test
  void checkSamplingEligibility_shouldReturnNoBlobs() {

    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ZERO, 0);

    final SamplingEligibilityStatus result =
        sampler.checkSamplingEligibility(block.getBeaconBlock().orElseThrow());

    assertEquals(SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS, result);
  }

  @Test
  void checkSamplingEligibility_shouldReturnOldEpoch() {

    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ZERO, 0);

    when(currentSlotProvider.getCurrentSlot()).thenReturn(UInt64.MAX_VALUE);

    final SamplingEligibilityStatus result =
        sampler.checkSamplingEligibility(block.getBeaconBlock().orElseThrow());

    assertEquals(SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH, result);
  }

  @Test
  void onRemoveAllForBlock_shouldPruneTrackers() {
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot slotAndBlockRootAdded = dataStructureUtil.randomSlotAndBlockRoot();
    final SlotAndBlockRoot slotAndBlockRootRemaining = dataStructureUtil.randomSlotAndBlockRoot();

    final DataColumnSamplingTracker completedTracker = mock(DataColumnSamplingTracker.class);
    when(completedTracker.completionFuture()).thenReturn(SafeFuture.completedFuture(null));
    when(completedTracker.blockRoot()).thenReturn(slotAndBlockRootRemaining.getBlockRoot());
    when(completedTracker.slot()).thenReturn(slotAndBlockRootRemaining.getSlot());

    final SafeFuture<List<UInt64>> completionFuture = new SafeFuture<>();
    final DataColumnSamplingTracker nonCompletedTracker = mock(DataColumnSamplingTracker.class);
    when(nonCompletedTracker.completionFuture()).thenReturn(completionFuture);
    when(nonCompletedTracker.blockRoot()).thenReturn(slotAndBlockRootAdded.getBlockRoot());
    when(nonCompletedTracker.slot()).thenReturn(slotAndBlockRootAdded.getSlot());

    sampler
        .getRecentlySampledColumnsByRoot()
        .put(slotAndBlockRootRemaining.getBlockRoot(), completedTracker);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(slotAndBlockRootAdded.getBlockRoot(), nonCompletedTracker);

    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .containsValues(completedTracker, nonCompletedTracker);

    sampler.removeAllForBlock(slotAndBlockRootAdded);

    assertThat(sampler.getRecentlySampledColumnsByRoot()).containsValues(completedTracker);
    assertThat(nonCompletedTracker.completionFuture()).isCancelled();

    // Non-existing - doesn't throw
    sampler.removeAllForBlock(slotAndBlockRoot);
  }

  @Test
  void onSlot_shouldPruneTrackers() {
    final UInt64 finalizedEpoch = UInt64.valueOf(1);
    final Bytes32 importedBlockRoot = dataStructureUtil.randomBytes32();
    final UInt64 lastFinalizedSlot = UInt64.valueOf(8);
    final UInt64 firstNonFinalizedSlot = UInt64.valueOf(9);

    when(recentChainData.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    when(recentChainData.containsBlock(any())).thenReturn(false);
    when(recentChainData.containsBlock(importedBlockRoot)).thenReturn(true);

    final DataColumnSamplingTracker partiallyCompletedTrackerBeforeFinalized =
        mock(DataColumnSamplingTracker.class);
    when(partiallyCompletedTrackerBeforeFinalized.completionFuture())
        .thenReturn(SafeFuture.completedFuture(null));
    when(partiallyCompletedTrackerBeforeFinalized.fullySampled())
        .thenReturn(new AtomicBoolean(false));
    when(partiallyCompletedTrackerBeforeFinalized.blockRoot())
        .thenReturn(dataStructureUtil.randomBytes32());
    when(partiallyCompletedTrackerBeforeFinalized.slot()).thenReturn(lastFinalizedSlot.decrement());

    final DataColumnSamplingTracker fullyCompletedTrackerBeforeFinalized =
        mock(DataColumnSamplingTracker.class);
    when(fullyCompletedTrackerBeforeFinalized.completionFuture())
        .thenReturn(SafeFuture.completedFuture(null));
    when(fullyCompletedTrackerBeforeFinalized.fullySampled()).thenReturn(new AtomicBoolean(true));
    when(fullyCompletedTrackerBeforeFinalized.blockRoot())
        .thenReturn(dataStructureUtil.randomBytes32());
    when(fullyCompletedTrackerBeforeFinalized.slot()).thenReturn(lastFinalizedSlot.decrement());

    // But not imported yet!
    final DataColumnSamplingTracker fullyCompletedTrackerAfterFinalized =
        mock(DataColumnSamplingTracker.class);
    when(fullyCompletedTrackerAfterFinalized.completionFuture())
        .thenReturn(SafeFuture.completedFuture(null));
    when(fullyCompletedTrackerAfterFinalized.fullySampled()).thenReturn(new AtomicBoolean(true));
    when(fullyCompletedTrackerAfterFinalized.blockRoot())
        .thenReturn(dataStructureUtil.randomBytes32());
    when(fullyCompletedTrackerAfterFinalized.slot()).thenReturn(lastFinalizedSlot.increment());

    final DataColumnSamplingTracker incompleteTrackerBeforeFinalized =
        mock(DataColumnSamplingTracker.class);
    final SafeFuture<List<UInt64>> incompleteTrackerFuture = new SafeFuture<>();
    when(incompleteTrackerBeforeFinalized.completionFuture()).thenReturn(incompleteTrackerFuture);
    when(incompleteTrackerBeforeFinalized.fullySampled()).thenReturn(new AtomicBoolean(false));
    when(incompleteTrackerBeforeFinalized.blockRoot())
        .thenReturn(dataStructureUtil.randomBytes32());
    when(incompleteTrackerBeforeFinalized.slot()).thenReturn(lastFinalizedSlot);

    final SafeFuture<List<UInt64>> incompleteTrackerForImportedBlockFuture = new SafeFuture<>();
    final DataColumnSamplingTracker incompleteTrackerForImportedBlock =
        mock(DataColumnSamplingTracker.class);
    when(incompleteTrackerForImportedBlock.completionFuture())
        .thenReturn(incompleteTrackerForImportedBlockFuture);
    when(incompleteTrackerForImportedBlock.fullySampled()).thenReturn(new AtomicBoolean(false));
    when(incompleteTrackerForImportedBlock.slot()).thenReturn(firstNonFinalizedSlot);
    when(incompleteTrackerForImportedBlock.blockRoot()).thenReturn(importedBlockRoot);

    sampler
        .getRecentlySampledColumnsByRoot()
        .put(
            partiallyCompletedTrackerBeforeFinalized.blockRoot(),
            partiallyCompletedTrackerBeforeFinalized);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(
            fullyCompletedTrackerBeforeFinalized.blockRoot(), fullyCompletedTrackerBeforeFinalized);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(fullyCompletedTrackerAfterFinalized.blockRoot(), fullyCompletedTrackerAfterFinalized);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(incompleteTrackerBeforeFinalized.blockRoot(), incompleteTrackerBeforeFinalized);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(incompleteTrackerForImportedBlock.blockRoot(), incompleteTrackerForImportedBlock);

    sampler.onSlot(UInt64.valueOf(20));

    // DA check is completed but fetch is not yet completed for this one
    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .containsExactly(
            entry(
                partiallyCompletedTrackerBeforeFinalized.blockRoot(),
                partiallyCompletedTrackerBeforeFinalized),
            entry(
                fullyCompletedTrackerAfterFinalized.blockRoot(),
                fullyCompletedTrackerAfterFinalized));
    assertThat(incompleteTrackerBeforeFinalized.completionFuture()).isCompletedExceptionally();
    assertThat(incompleteTrackerForImportedBlock.completionFuture()).isCompletedExceptionally();
    assertThat(partiallyCompletedTrackerBeforeFinalized.completionFuture()).isCompleted();
    assertThat(fullyCompletedTrackerBeforeFinalized.completionFuture()).isCompleted();
  }

  private void assertSamplerTracker(
      final Bytes32 blockRoot, final UInt64 slot, final List<UInt64> missingColumns) {
    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .hasEntrySatisfying(
            blockRoot,
            tracker -> {
              assertThat(tracker.missingColumns())
                  .containsExactlyInAnyOrderElementsOf(missingColumns);

              assertThat(tracker.getMissingColumnIdentifiers())
                  .containsExactlyInAnyOrderElementsOf(
                      missingColumns.stream()
                          .map(
                              columnIndex ->
                                  new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex))
                          .toList());
            });
  }

  private void assertSamplerTrackerHasRPCFetchScheduled(
      final Bytes32 blockRoot, final boolean expected) {
    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .hasEntrySatisfying(
            blockRoot,
            tracker -> assertThat(tracker.rpcFetchInProgress().get()).isEqualTo(expected));
  }

  private void assertRPCFetchInMillis(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final List<UInt64> missingColumns,
      final int millisFromNow) {

    if (millisFromNow > 0) {
      // advance up to one millis from the due
      stubTimeProvider.advanceTimeByMillis(millisFromNow - 1);
      // verify scheduled but not due for execution
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
      asyncRunner.executeDueActions();
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);

      // than advance to the due
      stubTimeProvider.advanceTimeByMillis(1);

      // due time arrived
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
      asyncRunner.executeDueActions();
    }

    // no delayed actions, we expect fetch already triggered
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);

    // verify we call retrieve for all missing columns
    for (final UInt64 missingColumn : missingColumns) {
      verify(retriever).retrieve(new DataColumnSlotAndIdentifier(slot, blockRoot, missingColumn));
    }
  }
}
