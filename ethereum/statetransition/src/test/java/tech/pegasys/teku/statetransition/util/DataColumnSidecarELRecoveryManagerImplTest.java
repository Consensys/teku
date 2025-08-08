/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand.createCustodyGroupCountManager;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELRecoveryManager;
import tech.pegasys.teku.statetransition.datacolumns.util.DataColumnSidecarELRecoveryManagerImpl;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarELRecoveryManagerImplTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 historicalTolerance = UInt64.valueOf(5);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final KZG kzg = mock(KZG.class);

  private final List<KZGCell> kzgCells =
      IntStream.range(0, 128).mapToObj(__ -> new KZGCell(Bytes.random(2048))).toList();

  @SuppressWarnings("unchecked")
  final Consumer<List<DataColumnSidecar>> dataColumnSidecarPublisher = mock(Consumer.class);

  private static final Duration EL_BLOBS_FETCHING_DELAY = Duration.ofMillis(500);
  private static final int EL_BLOBS_FETCHING_MAX_RETRIES = 3;

  final CustodyGroupCountManager custodyGroupCountManager = createCustodyGroupCountManager(4, 8);

  private final DataColumnSidecarELRecoveryManager dataColumnSidecarELRecoveryManager =
      new PoolFactory(metricsSystem)
          .createDataColumnSidecarELRecoveryManager(
              spec,
              asyncRunner,
              recentChainData,
              executionLayer,
              kzg,
              dataColumnSidecarPublisher,
              custodyGroupCountManager,
              metricsSystem,
              timeProvider);

  private UInt64 currentSlot = historicalTolerance.times(2);

  @BeforeEach
  public void setup() {
    when(executionLayer.engineGetBlobAndProofs(any(), eq(currentSlot)))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(kzg.computeCells(any())).thenReturn(kzgCells);
    setSlot(currentSlot);
  }

  private void setSlot(final UInt64 slot) {
    currentSlot = slot;
    dataColumnSidecarELRecoveryManager.onSlot(slot);
    when(recentChainData.computeTimeAtSlot(any())).thenReturn(UInt64.ZERO);
  }

  @Test
  public void onNewBlock_startsFromFuluBlocks() {
    final Spec minimalWithFuluForkEpoch =
        TestSpecFactory.createMinimalWithFuluForkEpoch(UInt64.ONE);
    final DataColumnSidecarELRecoveryManager dataColumnSidecarELRecoveryManagerCustom =
        new PoolFactory(metricsSystem)
            .createDataColumnSidecarELRecoveryManager(
                minimalWithFuluForkEpoch,
                asyncRunner,
                recentChainData,
                executionLayer,
                kzg,
                dataColumnSidecarPublisher,
                custodyGroupCountManager,
                metricsSystem,
                timeProvider);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    dataColumnSidecarELRecoveryManagerCustom.onSlot(UInt64.ONE);
    dataColumnSidecarELRecoveryManagerCustom.onNewBlock(block, Optional.empty());
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);

    final int fuluSlot = minimalWithFuluForkEpoch.slotsPerEpoch(UInt64.ZERO) + 1;
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(fuluSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    dataColumnSidecarELRecoveryManagerCustom.onSlot(UInt64.valueOf(fuluSlot));
    dataColumnSidecarELRecoveryManagerCustom.onNewBlock(block2, Optional.empty());
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
  }

  @Test
  public void onNewBlock_ignoresLocal() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.of(RemoteOrigin.LOCAL_PROPOSAL));
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);
  }

  @Test
  public void onNewDataColumnSidecar_ignoresLocalOrRecovered() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block, UInt64.ZERO);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    dataColumnSidecarELRecoveryManager.onNewDataColumnSidecar(
        dataColumnSidecar, RemoteOrigin.LOCAL_PROPOSAL);
    dataColumnSidecarELRecoveryManager.onNewDataColumnSidecar(
        dataColumnSidecar, RemoteOrigin.RECOVERED);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);
  }

  @Test
  public void onNewDataColumnSidecar_startsRecovery() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block, UInt64.ZERO);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    dataColumnSidecarELRecoveryManager.onNewDataColumnSidecar(
        dataColumnSidecar, RemoteOrigin.GOSSIP);
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
  }

  @Test
  public void shouldNotPublish_whenNotCurrentSlot() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot.minus(1));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);
  }

  @Test
  public void shouldNotPublish_whenNotAllBlobsRetrieved() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars =
        dataStructureUtil.randomBlobSidecarsForBlock(block).subList(0, 1);
    final List<BlobAndCellProofs> blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
    verifyNoInteractions(dataColumnSidecarPublisher);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldPublish_whenAllBlobsRetrieved() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobAndCellProofs> blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    final ArgumentCaptor<List<DataColumnSidecar>> dataColumnSidecarsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(dataColumnSidecarPublisher).accept(dataColumnSidecarsCaptor.capture());
    assertThat(dataColumnSidecarsCaptor.getValue().size()).isEqualTo(4);
  }

  @Test
  public void shouldRetry_whenElBlobsFetchingFails() {
    final DataColumnSidecarELRecoveryManager dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELRecoveryManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            kzg,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
    for (int i = 1; i <= EL_BLOBS_FETCHING_MAX_RETRIES; i++) {
      assertThat(asyncRunner.hasDelayedActions()).isTrue();
      asyncRunner.executeQueuedActions();
      verify(executionLayer, times(i + 1)).engineGetBlobAndCellProofsList(any(), any());
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(dataColumnSidecarPublisher);
  }

  @Test
  public void shouldRetry_whenMissingBlobsFromEl() {
    final DataColumnSidecarELRecoveryManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELRecoveryManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            kzg,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars =
        dataStructureUtil.randomBlobSidecarsForBlock(block).subList(0, 1);
    final List<BlobAndCellProofs> blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());
    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    for (int i = 1; i <= EL_BLOBS_FETCHING_MAX_RETRIES; i++) {
      assertThat(asyncRunner.hasDelayedActions()).isTrue();
      asyncRunner.executeQueuedActions();
      verify(executionLayer, times(i + 1))
          .engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(dataColumnSidecarPublisher);
  }

  @Test
  public void shouldRetry_whenEmptyBlobsListFromEl() {
    final DataColumnSidecarELRecoveryManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELRecoveryManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            kzg,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());
    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    for (int i = 1; i <= EL_BLOBS_FETCHING_MAX_RETRIES; i++) {
      assertThat(asyncRunner.hasDelayedActions()).isTrue();
      asyncRunner.executeQueuedActions();
      verify(executionLayer, times(i + 1))
          .engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(dataColumnSidecarPublisher);
  }

  @Test
  public void shouldStopRetry_whenDataColumnsAlreadyReceived() {
    final DataColumnSidecarELRecoveryManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELRecoveryManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            kzg,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobAndCellProofs> blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    // 2 first call fails and then the 3rd succeeds
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));

    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());

    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());
    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);

    // data columns received before the EL fetching is retried
    custodyGroupCountManager
        .getCustodyColumnIndices()
        .forEach(
            index ->
                dataColumnSidecarELRecoveryManager.onNewDataColumnSidecar(
                    dataStructureUtil.randomDataColumnSidecar(block.asHeader(), index),
                    RemoteOrigin.GOSSIP));
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(executionLayer);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldStopRetry_whenElBlobsFetchingIsDone() {
    final DataColumnSidecarELRecoveryManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELRecoveryManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            kzg,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobAndCellProofs> blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    // 2 first call fails and then the 3rd succeeds
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));

    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());

    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());

    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    for (int i = 1; i <= EL_BLOBS_FETCHING_MAX_RETRIES - 1; i++) {
      assertThat(asyncRunner.hasDelayedActions()).isTrue();
      asyncRunner.executeQueuedActions();
      // already called once, hence the +1
      verify(executionLayer, times(i + 1)).engineGetBlobAndCellProofsList(any(), any());
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    final ArgumentCaptor<List<DataColumnSidecar>> dataColumnSidecarsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(dataColumnSidecarPublisher).accept(dataColumnSidecarsCaptor.capture());
    assertThat(dataColumnSidecarsCaptor.getValue().size()).isEqualTo(4);
  }

  private List<VersionedHash> getVersionedHashes(
      final DataColumnSidecarELRecoveryManagerImpl dataColumnSidecarELRecoveryManager,
      final SlotAndBlockRoot slotAndBlockRoot) {
    final List<BlobIdentifier> missingBlobsIdentifiers =
        IntStream.range(
                0,
                dataColumnSidecarELRecoveryManager
                    .getRecoveryTask(slotAndBlockRoot)
                    .sszKZGCommitments()
                    .size())
            .mapToObj(
                index -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(index)))
            .toList();

    final SpecVersion specVersion = spec.atSlot(slotAndBlockRoot.getSlot());
    final MiscHelpersDeneb miscHelpersDeneb =
        specVersion.miscHelpers().toVersionDeneb().orElseThrow();

    final SszList<SszKZGCommitment> sszKZGCommitments =
        dataColumnSidecarELRecoveryManager.getRecoveryTask(slotAndBlockRoot).sszKZGCommitments();
    return missingBlobsIdentifiers.stream()
        .map(
            blobIdentifier ->
                miscHelpersDeneb.kzgCommitmentToVersionedHash(
                    sszKZGCommitments.get(blobIdentifier.getIndex().intValue()).getKZGCommitment()))
        .toList();
  }
}
