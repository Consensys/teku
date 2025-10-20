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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
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
import tech.pegasys.teku.storage.client.RecentChainData;

public class DasSamplerBasicTest {
  private static final Spec SPEC = TestSpecFactory.createMinimalFulu();
  private static final List<UInt64> SAMPLING_INDICES =
      List.of(UInt64.valueOf(5), UInt64.ZERO, UInt64.valueOf(2));

  private RecentChainData recentChainData;
  private CustodyGroupCountManager custodyGroupCountManager;
  private DataColumnSidecarCustody custody;
  private DataColumnSidecarRetriever retriever;
  private CurrentSlotProvider currentSlotProvider;
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, SPEC);

  private DasSamplerBasic sampler;

  @BeforeEach
  public void setUp() {
    custodyGroupCountManager = mock(CustodyGroupCountManager.class);
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
            currentSlotProvider,
            custody,
            retriever,
            () -> custodyGroupCountManager,
            recentChainData);
  }

  @Test
  void onAlreadyKnownDataColumn_shouldAddToTracker() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

    final DataColumnSlotAndIdentifier columnId =
        new DataColumnSlotAndIdentifier(UInt64.ZERO, blockRoot, SAMPLING_INDICES.getFirst());
    final List<UInt64> remainingColumns = SAMPLING_INDICES.subList(1, SAMPLING_INDICES.size());

    sampler.onAlreadyKnownDataColumn(columnId, RemoteOrigin.CUSTODY);

    assertSamplerTracker(blockRoot, UInt64.ZERO, remainingColumns);
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
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void checkDataAvailability_shouldCallRetrieverForMissingColumnsAndCallCustodyOnRetrieval() {
    final SignedBeaconBlockHeader block = dataStructureUtil.randomSignedBeaconBlockHeader();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(block.getMessage().getSlot(), block.getMessage().getRoot());

    final Map<UInt64, DataColumnSidecar> missingColumnSidecars =
        SAMPLING_INDICES.stream()
            .map(index -> Map.entry(index, dataStructureUtil.randomDataColumnSidecar(block, index)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
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

    // da check is requested
    sampler.checkDataAvailability(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    // verify we call retrieve for all missing columns
    for (final UInt64 missingColumn : SAMPLING_INDICES) {
      verify(retriever)
          .retrieve(
              new DataColumnSlotAndIdentifier(
                  slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), missingColumn));
    }

    // we receive one sidecar late, while retriever is still working
    sampler.onNewValidatedDataColumnSidecar(lateArrivedSidecar, RemoteOrigin.GOSSIP);

    // let the retriever complete all the futures
    futureSidecarsByIndex.forEach(
        (index, future) -> future.complete(missingColumnSidecars.get(index)));

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

    sampler.onAlreadyKnownDataColumn(source1, RemoteOrigin.CUSTODY);
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
}
