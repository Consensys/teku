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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.RemoteOrigin;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockBlobSidecarsTrackersPoolImplFuluTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 historicalTolerance = UInt64.valueOf(5);
  private final UInt64 futureTolerance = UInt64.valueOf(2);
  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final KZG kzg = mock(KZG.class);

  @SuppressWarnings("unchecked")
  private final Function<BlobSidecar, SafeFuture<Void>> blobSidecarPublisher = mock(Function.class);

  private final BlobSidecarGossipValidator blobSidecarGossipValidator =
      mock(BlobSidecarGossipValidator.class);

  private final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
  private final int maxItems = 15;
  private final List<KZGCellAndProof> kzgCellAndProofs =
      IntStream.range(0, 128)
          .mapToObj(
              __ ->
                  new KZGCellAndProof(
                      new KZGCell(Bytes.random(2048)), KZGProof.fromBytesCompressed(Bytes48.ZERO)))
          .toList();

  @SuppressWarnings("unchecked")
  final Consumer<List<DataColumnSidecar>> dataColumnSidecarPublisher = mock(Consumer.class);

  private final BlockBlobSidecarsTrackersPoolImpl blockBlobSidecarsTrackersPool =
      new PoolFactory(metricsSystem)
          .createPoolForBlockBlobSidecarsTrackers(
              blockImportChannel,
              spec,
              timeProvider,
              asyncRunner,
              recentChainData,
              executionLayer,
              () -> blobSidecarGossipValidator,
              blobSidecarPublisher,
              historicalTolerance,
              futureTolerance,
              maxItems,
              BlockBlobSidecarsTracker::new,
              true,
              kzg,
              dataColumnSidecarPublisher);

  private UInt64 currentSlot = historicalTolerance.times(2);

  @BeforeEach
  public void setup() {
    when(executionLayer.engineGetBlobs(any(), eq(currentSlot)))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(blobSidecarPublisher.apply(any())).thenReturn(SafeFuture.COMPLETE);
    when(kzg.computeCellsAndProofs(any())).thenReturn(kzgCellAndProofs);
    setSlot(currentSlot);
  }

  private void setSlot(final UInt64 slot) {
    currentSlot = slot;
    blockBlobSidecarsTrackersPool.onSlot(slot);
    when(recentChainData.computeTimeAtSlot(any())).thenReturn(UInt64.ZERO);
  }

  @Test
  public void onNewBlockNonSuperNode_shouldIgnoreFuluBlocks() {
    final BlockBlobSidecarsTrackersPoolImpl blockBlobSidecarsTrackersPoolCustom =
        new PoolFactory(new StubMetricsSystem())
            .createPoolForBlockBlobSidecarsTrackers(
                blockImportChannel,
                spec,
                timeProvider,
                asyncRunner,
                recentChainData,
                executionLayer,
                () -> blobSidecarGossipValidator,
                blobSidecarPublisher,
                historicalTolerance,
                futureTolerance,
                maxItems,
                BlockBlobSidecarsTracker::new,
                false,
                KZG.NOOP,
                __ -> {});
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    blockBlobSidecarsTrackersPoolCustom.onSlot(currentSlot);
    blockBlobSidecarsTrackersPoolCustom.onNewBlock(block, Optional.empty());

    assertThat(blockBlobSidecarsTrackersPoolCustom.containsBlock(block.getRoot())).isFalse();
    assertThat(blockBlobSidecarsTrackersPoolCustom.getTotalBlobSidecarsTrackers()).isEqualTo(0);
  }

  @Test
  public void superNodeOnNewBlock_shouldTrackFuluBlocks() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();
    assertThat(blockBlobSidecarsTrackersPool.getTotalBlobSidecarsTrackers()).isEqualTo(1);
  }

  @Test
  public void superNodeOnAllBlobSidecars_shouldPublishWhenOriginIsLocalEL() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());
    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();

    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecars.get(0), RemoteOrigin.LOCAL_EL);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecars.get(1), RemoteOrigin.LOCAL_EL);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecars.get(2), RemoteOrigin.LOCAL_EL);
    verifyNoInteractions(dataColumnSidecarPublisher);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecars.get(3), RemoteOrigin.LOCAL_EL);
    assertThat(
            blockBlobSidecarsTrackersPool
                .getBlobSidecarsTracker(block.getSlotAndBlockRoot())
                .isComplete())
        .isTrue();
    verify(dataColumnSidecarPublisher, times(1)).accept(anyList());
    verifyNoInteractions(blobSidecarPublisher);
  }

  @Test
  public void superNodeOnAllBlobSidecars_shouldNotPublishWhenOriginIsLocalELIsNotCurrentSlot() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    blockBlobSidecarsTrackersPool.onSlot(currentSlot.minus(1));
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());
    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();

    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecars.get(0), RemoteOrigin.LOCAL_EL);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecars.get(1), RemoteOrigin.LOCAL_EL);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecars.get(2), RemoteOrigin.LOCAL_EL);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecars.get(3), RemoteOrigin.LOCAL_EL);
    assertThat(
            blockBlobSidecarsTrackersPool
                .getBlobSidecarsTracker(block.getSlotAndBlockRoot())
                .isComplete())
        .isTrue();
    verifyNoInteractions(dataColumnSidecarPublisher);
    verifyNoInteractions(blobSidecarPublisher);
  }

  @Test
  void shouldNotFetchMissingBlobSidecarsViaRPCWhenELLookupFails() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    final List<BlobIdentifier> requiredBlobSidecarEvents = new ArrayList<>();
    blockBlobSidecarsTrackersPool.subscribeRequiredBlobSidecar(requiredBlobSidecarEvents::add);

    final Set<BlobIdentifier> missingBlobs =
        Set.of(
            new BlobIdentifier(block.getRoot(), UInt64.ONE),
            new BlobIdentifier(block.getRoot(), UInt64.ZERO));

    final Function<SlotAndBlockRoot, BlockBlobSidecarsTracker> mockedTrackersFactory =
        (slotAndRoot) -> {
          BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
          when(tracker.getMissingBlobSidecars()).thenAnswer(__ -> missingBlobs.stream());
          when(tracker.getBlock()).thenReturn(Optional.of(block));
          return tracker;
        };
    final BlockBlobSidecarsTrackersPoolImpl blockBlobSidecarsTrackersPoolCustom =
        new PoolFactory(new StubMetricsSystem())
            .createPoolForBlockBlobSidecarsTrackers(
                blockImportChannel,
                spec,
                timeProvider,
                asyncRunner,
                recentChainData,
                executionLayer,
                () -> blobSidecarGossipValidator,
                blobSidecarPublisher,
                historicalTolerance,
                futureTolerance,
                maxItems,
                mockedTrackersFactory::apply,
                true,
                kzg,
                dataColumnSidecarPublisher);
    blockBlobSidecarsTrackersPoolCustom.onSlot(currentSlot);

    // prepare failure from EL
    when(executionLayer.engineGetBlobs(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("oops")));

    blockBlobSidecarsTrackersPoolCustom.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();

    verify(executionLayer).engineGetBlobs(any(), any());
    assertThat(requiredBlobSidecarEvents).isEmpty();
  }
}
