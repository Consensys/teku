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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
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

public class DasSamplerBasicImplTest {
  private static final Spec SPEC = TestSpecFactory.createMinimalFulu();
  private static final List<UInt64> SAMPLING_INDICES =
      List.of(UInt64.valueOf(5), UInt64.ZERO, UInt64.valueOf(2));

  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(stubTimeProvider);
  private final RPCFetchDelayProvider rpcFetchDelayProvider = mock(RPCFetchDelayProvider.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private RecentChainData recentChainData;
  private DataColumnSidecarCustody custody;
  private DataColumnSidecarRetriever retriever;
  private CurrentSlotProvider currentSlotProvider;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, SPEC);

  private DasSamplerBasicImpl sampler;

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
        new DasSamplerBasicImpl(
            SPEC,
            asyncRunner,
            currentSlotProvider,
            rpcFetchDelayProvider,
            custody,
            retriever,
            custodyGroupCountManager,
            recentChainData,
            metricsSystem);
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
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(block.getSlot(), block.getRoot());

    when(retriever.retrieve(any())).thenReturn(new SafeFuture<>());

    final SafeFuture<List<UInt64>> completionFuture = sampler.checkDataAvailability(block);

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
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlockHeader blockHeader = block.asHeader();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(block.getMessage().getSlot(), block.getMessage().getRoot());

    final Map<UInt64, DataColumnSidecar> missingColumnSidecars =
        SAMPLING_INDICES.stream()
            .map(
                index ->
                     dataStructureUtil.randomDataColumnSidecar(blockHeader, index))
            .collect(
                Collectors.toUnmodifiableMap(DataColumnSidecar::getIndex, Function.identity()));
    final Map<UInt64, SafeFuture<DataColumnSidecar>> futureSidecarsByIndex =
        missingColumnSidecars.values().stream()
            .map(DataColumnSidecar::getIndex)
            .map(index -> Map.entry(index, new SafeFuture<DataColumnSidecar>()))
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
    sampler.checkDataAvailability(block);

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
      final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
      final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(block.getMessage().getSlot(), block.getMessage().getRoot());

    when(retriever.retrieve(any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("error")));

    final SafeFuture<List<UInt64>> completionFuture =
        sampler.checkDataAvailability(block);

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
        sampler.checkDataAvailability(block);

    assertSamplerTracker(
        slotAndBlockRoot.getBlockRoot(), slotAndBlockRoot.getSlot(), SAMPLING_INDICES);

    verify(retriever, times(SAMPLING_INDICES.size() * 2)).retrieve(any());

    assertThat(secondCompletionFuture).isSameAs(completionFuture);
    assertSamplerTrackerHasRPCFetchScheduled(slotAndBlockRoot.getBlockRoot(), true);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void checkDataAvailability_shouldRPCFetchImmediatelyIfNotPreviouslyScheduled() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(block.getMessage().getSlot(), block.getMessage().getRoot());

    when(retriever.retrieve(any())).thenReturn(new SafeFuture<>());

    when(rpcFetchDelayProvider.calculate(slotAndBlockRoot.getSlot())).thenReturn(Duration.ZERO);

    sampler.checkDataAvailability(block);

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
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    sampler.onNewValidatedDataColumnSidecar(source1, RemoteOrigin.CUSTODY);
    sampler.onNewValidatedDataColumnSidecar(source2, RemoteOrigin.RPC);
    sampler.checkDataAvailability(block);

    assertThat(sampler.getRecentlySampledColumnsByRoot())
        .containsKey(source1.blockRoot())
        .containsKey(source2.getBeaconBlockRoot())
        .containsKey(block.getRoot());
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
  void onSlot_shouldPruneTrackers() {
    final UInt64 finalizedEpoch = UInt64.valueOf(1);
    final Bytes32 importedBlockRoot = dataStructureUtil.randomBytes32();
    final UInt64 lastFinalizedSlot = UInt64.valueOf(8);
    final UInt64 firstNonFinalizedSlot = UInt64.valueOf(9);

    when(recentChainData.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    when(recentChainData.containsBlock(any())).thenReturn(false);
    when(recentChainData.containsBlock(importedBlockRoot)).thenReturn(true);

    final DataColumnSamplingTracker completedTracker = mock(DataColumnSamplingTracker.class);
    when(completedTracker.completionFuture()).thenReturn(SafeFuture.completedFuture(null));

    final SafeFuture<List<UInt64>> trackerForFinalizedSlotFuture = new SafeFuture<>();
    final DataColumnSamplingTracker trackerForFinalizedSlot = mock(DataColumnSamplingTracker.class);
    when(trackerForFinalizedSlot.completionFuture()).thenReturn(trackerForFinalizedSlotFuture);
    when(trackerForFinalizedSlot.slot()).thenReturn(lastFinalizedSlot);

    final SafeFuture<List<UInt64>> trackerForImportedBlockFuture = new SafeFuture<>();
    final DataColumnSamplingTracker trackerForImportedBlock = mock(DataColumnSamplingTracker.class);
    when(trackerForImportedBlock.completionFuture()).thenReturn(trackerForImportedBlockFuture);
    when(trackerForImportedBlock.slot()).thenReturn(firstNonFinalizedSlot);
    when(trackerForImportedBlock.blockRoot()).thenReturn(importedBlockRoot);

    final DataColumnSamplingTracker expectedToRemainTracker = mock(DataColumnSamplingTracker.class);
    when(expectedToRemainTracker.completionFuture()).thenReturn(new SafeFuture<>());
    when(expectedToRemainTracker.slot()).thenReturn(firstNonFinalizedSlot);
    when(expectedToRemainTracker.blockRoot()).thenReturn(dataStructureUtil.randomBytes32());

    sampler
        .getRecentlySampledColumnsByRoot()
        .put(dataStructureUtil.randomBytes32(), completedTracker);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(dataStructureUtil.randomBytes32(), trackerForFinalizedSlot);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(dataStructureUtil.randomBytes32(), trackerForImportedBlock);
    sampler
        .getRecentlySampledColumnsByRoot()
        .put(dataStructureUtil.randomBytes32(), expectedToRemainTracker);

    sampler.onSlot(UInt64.valueOf(20));

    assertThat(sampler.getRecentlySampledColumnsByRoot()).containsValue(expectedToRemainTracker);
    assertThat(trackerForImportedBlockFuture).isCompletedExceptionally();
    assertThat(trackerForFinalizedSlotFuture).isCompletedExceptionally();
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
            tracker -> assertThat(tracker.rpcFetchScheduled().get()).isEqualTo(expected));
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
