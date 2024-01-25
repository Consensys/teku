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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl.GAUGE_BLOB_SIDECARS_LABEL;
import static tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl.GAUGE_BLOB_SIDECARS_TRACKERS_LABEL;
import static tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl.MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS;
import static tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl.MIN_WAIT_MILLIS;
import static tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl.TARGET_WAIT_MILLIS;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.RemoteOrigin;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockBlobSidecarsTrackersPoolImplTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 historicalTolerance = UInt64.valueOf(5);
  private final UInt64 futureTolerance = UInt64.valueOf(2);
  private final ObservableMetricsSystem metricsSystem =
      new PrometheusMetricsSystem(Set.of(TekuMetricCategory.BEACON), false);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final int maxItems = 15;
  private final BlockBlobSidecarsTrackersPoolImpl blockBlobSidecarsTrackersPool =
      new PoolFactory(metricsSystem)
          .createPoolForBlockBlobSidecarsTrackers(
              spec,
              timeProvider,
              asyncRunner,
              recentChainData,
              historicalTolerance,
              futureTolerance,
              maxItems,
              this::trackerFactory);

  private UInt64 currentSlot = historicalTolerance.times(2);
  private final List<Bytes32> requiredBlockRootEvents = new ArrayList<>();
  private final List<Bytes32> requiredBlockRootDroppedEvents = new ArrayList<>();
  private final List<BlobIdentifier> requiredBlobSidecarEvents = new ArrayList<>();
  private final List<BlobIdentifier> requiredBlobSidecarDroppedEvents = new ArrayList<>();
  private final List<BlobSidecar> newBlobSidecarEvents = new ArrayList<>();

  private Optional<Function<SlotAndBlockRoot, BlockBlobSidecarsTracker>> mockedTrackersFactory =
      Optional.empty();

  @BeforeEach
  public void setup() {
    // Set up slot
    blockBlobSidecarsTrackersPool.subscribeRequiredBlockRoot(requiredBlockRootEvents::add);
    blockBlobSidecarsTrackersPool.subscribeRequiredBlockRootDropped(
        requiredBlockRootDroppedEvents::add);
    blockBlobSidecarsTrackersPool.subscribeRequiredBlobSidecar(requiredBlobSidecarEvents::add);
    blockBlobSidecarsTrackersPool.subscribeRequiredBlobSidecarDropped(
        requiredBlobSidecarDroppedEvents::add);
    blockBlobSidecarsTrackersPool.subscribeNewBlobSidecar(newBlobSidecarEvents::add);
    setSlot(currentSlot);
  }

  private void setSlot(final long slot) {
    setSlot(UInt64.valueOf(slot));
  }

  private void setSlot(final UInt64 slot) {
    currentSlot = slot;
    blockBlobSidecarsTrackersPool.onSlot(slot);
    when(recentChainData.computeTimeAtSlot(any())).thenReturn(UInt64.ZERO);
  }

  @Test
  public void onNewBlock_addTrackerWithBlock() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();
    assertThat(blockBlobSidecarsTrackersPool.getBlock(block.getRoot())).contains(block);
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void onNewBlobSidecar_addTrackerWithBlobSidecarIgnoringDuplicates() {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(dataStructureUtil.randomSignedBeaconBlockHeader(currentSlot))
            .build();

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertThat(
            blockBlobSidecarsTrackersPool.containsBlobSidecar(
                blobIdentifierFromBlobSidecar(blobSidecar)))
        .isTrue();
    assertThat(
            blockBlobSidecarsTrackersPool.getBlobSidecar(
                blobSidecar.getBlockRoot(), blobSidecar.getIndex()))
        .contains(blobSidecar);
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
    assertThat(newBlobSidecarEvents).containsExactly(blobSidecar);

    assertBlobSidecarsCount(1);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void onNewBlobSidecar_shouldIgnoreDuplicates() {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(dataStructureUtil.randomSignedBeaconBlockHeader(currentSlot))
            .build();

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertThat(
            blockBlobSidecarsTrackersPool.containsBlobSidecar(
                blobIdentifierFromBlobSidecar(blobSidecar)))
        .isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
    assertThat(newBlobSidecarEvents).containsExactly(blobSidecar);

    assertBlobSidecarsCount(1);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void onNewBlock_shouldIgnorePreDenebBlocks() {
    final Spec spec = TestSpecFactory.createMainnetCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isFalse();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(0);
  }

  @Test
  public void
      onNewBlobSidecar_onNewBlock_onCompletedBlockAndBlobSidecars_shouldIgnoreAlreadyImportedBlocks() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block.asHeader())
            .build();

    when(recentChainData.containsBlock(blobSidecar.getBlockRoot())).thenReturn(true);

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());
    blockBlobSidecarsTrackersPool.onCompletedBlockAndBlobSidecars(block, List.of(blobSidecar));

    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isFalse();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
    assertThat(newBlobSidecarEvents).isEmpty();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(0);
  }

  @Test
  public void onNewBlobSidecarOnNewBlock_addTrackerWithBothBlockAndBlobSidecar() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecarsForBlock(block).get(0);

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(
            blockBlobSidecarsTrackersPool.containsBlobSidecar(
                blobIdentifierFromBlobSidecar(blobSidecar)))
        .isTrue();
    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(newBlobSidecarEvents).containsExactly(blobSidecar);

    assertBlobSidecarsCount(1);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void twoOnNewBlobSidecar_addTrackerWithBothBlobSidecars() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);

    final BlobSidecar blobSidecar0 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block.asHeader())
            .index(UInt64.ZERO)
            .build();

    final BlobSidecar blobSidecar1 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block.asHeader())
            .index(UInt64.ONE)
            .build();

    final BlobSidecar blobSidecar1bis =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block.asHeader())
            .index(UInt64.ONE)
            .build();

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar0, RemoteOrigin.GOSSIP);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar1, RemoteOrigin.GOSSIP);
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar1bis, RemoteOrigin.GOSSIP);

    assertThat(
            blockBlobSidecarsTrackersPool.containsBlobSidecar(
                blobIdentifierFromBlobSidecar(blobSidecar0)))
        .isTrue();
    assertThat(
            blockBlobSidecarsTrackersPool.containsBlobSidecar(
                blobIdentifierFromBlobSidecar(blobSidecar1)))
        .isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
    assertThat(newBlobSidecarEvents).containsExactly(blobSidecar0, blobSidecar1);

    assertBlobSidecarsCount(2);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void twoOnNewBlock_addTrackerWithBothBlobSidecars() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final SignedBeaconBlock blockAtPreviousSlot =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue() - 1);

    blockBlobSidecarsTrackersPool.onNewBlock(blockAtPreviousSlot, Optional.empty());
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(blockBlobSidecarsTrackersPool.containsBlock(blockAtPreviousSlot.getRoot())).isTrue();
    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
    assertThat(newBlobSidecarEvents).isEmpty();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(2);
  }

  @Test
  public void onCompletedBlockAndBlobSidecars_shouldCreateTrackerIgnoringHistoricalTolerance() {
    final UInt64 slot = currentSlot.minus(historicalTolerance).minus(UInt64.ONE);

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);

    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);

    blockBlobSidecarsTrackersPool.onCompletedBlockAndBlobSidecars(block, blobSidecars);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
    assertThat(newBlobSidecarEvents).containsExactlyElementsOf(blobSidecars);

    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackersPool.getBlobSidecarsTracker(block.getSlotAndBlockRoot());

    assertThat(blockBlobSidecarsTracker.getBlobSidecars().values())
        .containsExactlyInAnyOrderElementsOf(blobSidecars);
    assertThat(blockBlobSidecarsTracker.getBlock()).isPresent();
    assertThat(blockBlobSidecarsTracker.isFetchTriggered()).isFalse();
    assertThatSafeFuture(blockBlobSidecarsTracker.getCompletionFuture()).isCompleted();

    assertBlobSidecarsCount(blobSidecars.size());
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void onCompletedBlockAndBlobSidecars_shouldNotTriggerFetch() {

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);

    final List<BlobSidecar> blobSidecars = List.of();

    blockBlobSidecarsTrackersPool.onCompletedBlockAndBlobSidecars(block, blobSidecars);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
    assertThat(newBlobSidecarEvents).containsExactlyElementsOf(blobSidecars);

    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackersPool.getBlobSidecarsTracker(block.getSlotAndBlockRoot());

    assertThat(blockBlobSidecarsTracker.getBlobSidecars().values())
        .containsExactlyInAnyOrderElementsOf(blobSidecars);
    assertThat(blockBlobSidecarsTracker.getBlock()).isPresent();
    assertThat(blockBlobSidecarsTracker.isFetchTriggered()).isFalse();
    assertThatSafeFuture(blockBlobSidecarsTracker.getCompletionFuture()).isNotCompleted();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void
      getOrCreateBlocBlobSidecarsTracker_createATrackerWithBlockSetIgnoringHistoricalTolerance() {
    final UInt64 slot = currentSlot.minus(historicalTolerance).minus(UInt64.ONE);

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);

    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackersPool.getOrCreateBlockBlobSidecarsTracker(block);

    assertThat(blockBlobSidecarsTracker.getBlock()).isPresent();
    assertThat(blockBlobSidecarsTracker.isFetchTriggered()).isFalse();
    assertThatSafeFuture(blockBlobSidecarsTracker.getCompletionFuture()).isNotCompleted();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void shouldApplyIgnoreForBlock() {
    final UInt64 slot = currentSlot.plus(futureTolerance).plus(UInt64.ONE);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());

    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isFalse();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(0);
  }

  @Test
  public void shouldApplyIgnoreForBlobSidecar() {
    final UInt64 slot = currentSlot.plus(futureTolerance).plus(UInt64.ONE);
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(dataStructureUtil.randomSignedBeaconBlockHeader(slot))
            .build();

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertThat(
            blockBlobSidecarsTrackersPool.containsBlobSidecar(
                blobIdentifierFromBlobSidecar(blobSidecar)))
        .isFalse();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(0);
  }

  @Test
  public void add_moreThanMaxItems() {
    for (int i = 0; i < maxItems * 2; i++) {
      final SignedBeaconBlock block =
          dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
      blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

      final int expectedSize = Math.min(maxItems, i + 1);
      assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();
      assertThat(blockBlobSidecarsTrackersPool.getTotalBlobSidecarsTrackers())
          .isEqualTo(expectedSize);
      assertBlobSidecarsTrackersCount(expectedSize);
    }

    // Final sanity check
    assertThat(blockBlobSidecarsTrackersPool.getTotalBlobSidecarsTrackers()).isEqualTo(maxItems);
    assertBlobSidecarsTrackersCount(maxItems);

    assertBlobSidecarsCount(0);
  }

  @Test
  public void prune_finalizedBlocks() {
    final SignedBeaconBlock finalizedBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final Checkpoint checkpoint = finalizedCheckpoint(finalizedBlock);
    final long finalizedSlot = checkpoint.getEpochStartSlot(spec).longValue();
    setSlot(finalizedSlot);

    // Add a bunch of blocks
    List<SignedBeaconBlock> nonFinalBlocks =
        List.of(dataStructureUtil.randomSignedBeaconBlock(finalizedSlot + 1));
    List<SignedBeaconBlock> finalizedBlocks =
        List.of(
            dataStructureUtil.randomSignedBeaconBlock(finalizedSlot),
            dataStructureUtil.randomSignedBeaconBlock(finalizedSlot - 1));
    List<SignedBeaconBlock> allBlocks = new ArrayList<>();
    allBlocks.addAll(nonFinalBlocks);
    allBlocks.addAll(finalizedBlocks);
    nonFinalBlocks.forEach(
        block -> blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty()));
    finalizedBlocks.forEach(
        block -> blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty()));

    // Check that all blocks are in the collection
    assertBlobSidecarsTrackersCount(finalizedBlocks.size() + nonFinalBlocks.size());
    for (SignedBeaconBlock block : allBlocks) {
      assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();
    }

    // Update finalized checkpoint and prune
    blockBlobSidecarsTrackersPool.onNewFinalizedCheckpoint(checkpoint, false);
    blockBlobSidecarsTrackersPool.prune();

    // Check that all final blocks have been pruned
    assertBlobSidecarsTrackersCount(nonFinalBlocks.size());
    for (SignedBeaconBlock block : nonFinalBlocks) {
      assertThat(blockBlobSidecarsTrackersPool.containsBlock(block.getRoot())).isTrue();
    }
  }

  @Test
  void shouldFetchMissingBlobSidecars() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);

    final Set<BlobIdentifier> missingBlobs =
        Set.of(
            new BlobIdentifier(block.getRoot(), UInt64.ONE),
            new BlobIdentifier(block.getRoot(), UInt64.ZERO));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getMissingBlobSidecars()).thenReturn(missingBlobs.stream());
              when(tracker.getBlock()).thenReturn(Optional.of(block));
              return tracker;
            });

    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();

    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).containsExactlyElementsOf(missingBlobs);
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
  }

  @Test
  void shouldFetchMissingBlockAndBlobSidecars() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block.asHeader())
            .index(UInt64.valueOf(2))
            .build();

    final Set<BlobIdentifier> missingBlobs =
        Set.of(
            new BlobIdentifier(block.getRoot(), UInt64.ONE),
            new BlobIdentifier(block.getRoot(), UInt64.ZERO));

    final BlockBlobSidecarsTracker mockedTracker = mock(BlockBlobSidecarsTracker.class);
    when(mockedTracker.getBlock()).thenReturn(Optional.empty());
    when(mockedTracker.getMissingBlobSidecars()).thenReturn(missingBlobs.stream());
    when(mockedTracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());

    mockedTrackersFactory = Optional.of((__) -> mockedTracker);

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();

    verify(mockedTracker).setFetchTriggered();

    assertThat(requiredBlockRootEvents).containsExactly(block.getRoot());
    assertThat(requiredBlobSidecarEvents).containsExactlyElementsOf(missingBlobs);

    assertStats("block", "rpc_fetch", 1);
    assertStats("blob_sidecar", "rpc_fetch", missingBlobs.size());

    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
  }

  @Test
  void shouldDropBlobSidecarsThatHasBeenFetchedButNotPresentInBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);

    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(currentSlot, block.getRoot());
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block.asHeader())
            .index(UInt64.valueOf(2))
            .build();

    final Set<BlobIdentifier> blobsNotUserInBlock =
        Set.of(
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(2)),
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(3)));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getBlock()).thenReturn(Optional.empty());
              when(tracker.getSlotAndBlockRoot()).thenReturn(slotAndBlockRoot);
              when(tracker.setBlock(block)).thenReturn(true);
              when(tracker.isFetchTriggered()).thenReturn(true);
              when(tracker.getUnusedBlobSidecarsForBlock())
                  .thenReturn(blobsNotUserInBlock.stream());
              return tracker;
            });

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(requiredBlobSidecarDroppedEvents).containsExactlyElementsOf(blobsNotUserInBlock);
  }

  @Test
  void shouldNotDropUnusedBlobSidecarsIfFetchingHasNotOccurred() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);

    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(currentSlot, block.getRoot());
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block.asHeader())
            .index(UInt64.valueOf(2))
            .build();

    final Set<BlobIdentifier> blobsNotUserInBlock =
        Set.of(
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(2)),
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(3)));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getBlock()).thenReturn(Optional.empty());
              when(tracker.getSlotAndBlockRoot()).thenReturn(slotAndBlockRoot);
              when(tracker.setBlock(block)).thenReturn(true);
              when(tracker.isFetchTriggered()).thenReturn(false);
              when(tracker.getUnusedBlobSidecarsForBlock())
                  .thenReturn(blobsNotUserInBlock.stream());
              return tracker;
            });

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
  }

  @Test
  public void shouldFetchContentWhenBlockIsNotForCurrentSlot() {
    final UInt64 slot = currentSlot.minus(UInt64.ONE);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);

    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
  }

  @Test
  public void shouldFetchContentWhenBlobSidecarIsNotForCurrentSlot() {
    final UInt64 slot = currentSlot.minus(UInt64.ONE);
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(dataStructureUtil.randomSignedBeaconBlockHeader(slot))
            .build();

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
  }

  @Test
  void shouldDropPossiblyFetchedBlobSidecars() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(currentSlot);

    final Set<BlobIdentifier> missingBlobs =
        Set.of(
            new BlobIdentifier(block.getRoot(), UInt64.ONE),
            new BlobIdentifier(block.getRoot(), UInt64.ZERO));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getMissingBlobSidecars()).thenReturn(missingBlobs.stream());
              when(tracker.getBlock()).thenReturn(Optional.of(block));
              when(tracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());
              when(tracker.isFetchTriggered()).thenReturn(true);
              return tracker;
            });

    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    blockBlobSidecarsTrackersPool.removeAllForBlock(block.getRoot());

    assertThat(requiredBlobSidecarDroppedEvents).containsExactlyElementsOf(missingBlobs);

    // subsequent fetch will not try to fetch anything
    asyncRunner.executeQueuedActions();

    assertThat(requiredBlobSidecarEvents).isEmpty();
  }

  @Test
  void shouldDropPossiblyFetchedBlock() {
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(signedBeaconBlock.asHeader())
            .build();

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getBlock()).thenReturn(Optional.empty());
              when(tracker.getSlotAndBlockRoot())
                  .thenReturn(signedBeaconBlock.getSlotAndBlockRoot());
              when(tracker.isFetchTriggered()).thenReturn(true);
              return tracker;
            });

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    blockBlobSidecarsTrackersPool.removeAllForBlock(signedBeaconBlock.getRoot());

    assertThat(requiredBlockRootDroppedEvents).containsExactly(signedBeaconBlock.getRoot());

    // subsequent fetch will not try to fetch anything
    asyncRunner.executeQueuedActions();

    assertThat(requiredBlockRootEvents).isEmpty();
  }

  @Test
  void shouldNotDropPossiblyFetchedBlockIfFetchHasNotOccurred() {
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(signedBeaconBlock.asHeader())
            .build();

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getBlock()).thenReturn(Optional.empty());
              when(tracker.getSlotAndBlockRoot())
                  .thenReturn(signedBeaconBlock.getSlotAndBlockRoot());
              when(tracker.isFetchTriggered()).thenReturn(false);
              return tracker;
            });

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    blockBlobSidecarsTrackersPool.removeAllForBlock(signedBeaconBlock.getRoot());

    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
  }

  @Test
  void shouldRespectTargetWhenBlockIsEarly() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot, dataStructureUtil.randomBytes32());

    final UInt64 startSlotInSeconds = UInt64.valueOf(10);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    // blocks arrives at slot start
    timeProvider.advanceTimeBySeconds(startSlotInSeconds.longValue());

    final Duration fetchDelay = blockBlobSidecarsTrackersPool.calculateFetchDelay(slotAndBlockRoot);

    // we can wait the full target
    assertThat(fetchDelay).isEqualTo(Duration.ofMillis(TARGET_WAIT_MILLIS.longValue()));
  }

  @Test
  void calculateFetchDelay_shouldRespectMinimumWhenBlockIsLate() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot, dataStructureUtil.randomBytes32());

    final UInt64 startSlotInSeconds = UInt64.valueOf(10);
    final UInt64 startSlotInMillis = startSlotInSeconds.times(1_000);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    // blocks arrives 200ms before attestation due
    timeProvider.advanceTimeByMillis(startSlotInMillis.plus(3_800).longValue());

    final Duration fetchDelay = blockBlobSidecarsTrackersPool.calculateFetchDelay(slotAndBlockRoot);

    // we can wait the full target
    assertThat(fetchDelay).isEqualTo(Duration.ofMillis(MIN_WAIT_MILLIS.longValue()));
  }

  @Test
  void calculateFetchDelay_shouldRespectTargetWhenBlockIsVeryLate() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot, dataStructureUtil.randomBytes32());

    final UInt64 startSlotInSeconds = UInt64.valueOf(10);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    // blocks arrives 1s after attestation due
    timeProvider.advanceTimeBySeconds(startSlotInSeconds.plus(5).longValue());

    final Duration fetchDelay = blockBlobSidecarsTrackersPool.calculateFetchDelay(slotAndBlockRoot);

    // we can wait the full target
    assertThat(fetchDelay).isEqualTo(Duration.ofMillis(TARGET_WAIT_MILLIS.longValue()));
  }

  @Test
  void calculateFetchDelay_shouldRespectAttestationDueLimit() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot, dataStructureUtil.randomBytes32());

    final UInt64 startSlotInSeconds = UInt64.valueOf(10);
    final UInt64 startSlotInMillis = startSlotInSeconds.times(1_000);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    final UInt64 millisecondsIntoAttDueLimit = UInt64.valueOf(200);

    // block arrival is 200ms over the max wait relative to the attestation due
    final UInt64 blockArrivalTimeMillis =
        startSlotInMillis
            .plus(4_000)
            .minus(MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS.minus(millisecondsIntoAttDueLimit))
            .minus(TARGET_WAIT_MILLIS);

    timeProvider.advanceTimeByMillis(blockArrivalTimeMillis.longValue());

    final Duration fetchDelay = blockBlobSidecarsTrackersPool.calculateFetchDelay(slotAndBlockRoot);

    // we can only wait 200ms less than target
    assertThat(fetchDelay)
        .isEqualTo(
            Duration.ofMillis(TARGET_WAIT_MILLIS.minus(millisecondsIntoAttDueLimit).longValue()));
  }

  @Test
  void calculateFetchDelay_shouldReturnZeroIfSlotIsOld() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot.minus(1), dataStructureUtil.randomBytes32());

    final Duration fetchDelay = blockBlobSidecarsTrackersPool.calculateFetchDelay(slotAndBlockRoot);

    assertThat(fetchDelay).isEqualTo(Duration.ZERO);
  }

  @Test
  void getAllRequiredBlobSidecars_shouldReturnAllRequiredBlobSidecars() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(currentSlot);

    final Set<BlobIdentifier> missingBlobs1 =
        Set.of(
            new BlobIdentifier(block1.getRoot(), UInt64.ONE),
            new BlobIdentifier(block1.getRoot(), UInt64.ZERO));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getMissingBlobSidecars()).thenReturn(missingBlobs1.stream());
              when(tracker.getBlock()).thenReturn(Optional.of(block1));
              return tracker;
            });

    blockBlobSidecarsTrackersPool.onNewBlock(block1, Optional.empty());

    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(currentSlot);

    final Set<BlobIdentifier> missingBlobs2 =
        Set.of(
            new BlobIdentifier(block2.getRoot(), UInt64.ONE),
            new BlobIdentifier(block2.getRoot(), UInt64.valueOf(2)));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getMissingBlobSidecars()).thenReturn(missingBlobs2.stream());
              when(tracker.getBlock()).thenReturn(Optional.of(block2));
              return tracker;
            });

    blockBlobSidecarsTrackersPool.onNewBlock(block2, Optional.empty());

    final Set<BlobIdentifier> allMissing =
        Stream.concat(missingBlobs1.stream(), missingBlobs2.stream()).collect(Collectors.toSet());

    assertThat(blockBlobSidecarsTrackersPool.getAllRequiredBlobSidecars())
        .containsExactlyElementsOf(allMissing);
  }

  @Test
  void stats_onNewBlobSidecar() {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(dataStructureUtil.randomSignedBeaconBlockHeader(currentSlot))
            .build();

    // new from gossip
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertStats("blob_sidecar", "gossip", 1);

    // duplicate from gossip
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.GOSSIP);

    assertStats("blob_sidecar", "gossip", 1);
    assertStats("blob_sidecar", "gossip_duplicate", 1);

    // duplicate from RPC

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar, RemoteOrigin.RPC);

    assertStats("blob_sidecar", "gossip", 1);
    assertStats("blob_sidecar", "rpc_duplicate", 1);

    final BlobSidecar blobSidecar2 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(
                dataStructureUtil.randomSignedBeaconBlockHeader(currentSlot.increment()))
            .build();

    // new from RPC
    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar2, RemoteOrigin.RPC);

    assertStats("blob_sidecar", "gossip", 1);
    assertStats("blob_sidecar", "rpc", 1);
    assertStats("blob_sidecar", "gossip_duplicate", 1);
    assertStats("blob_sidecar", "rpc_duplicate", 1);
  }

  @Test
  void stats_onNewBlock() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    // new from gossip
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.of(RemoteOrigin.GOSSIP));

    assertStats("block", "gossip", 1);

    // duplicate from gossip
    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.of(RemoteOrigin.GOSSIP));

    assertStats("block", "gossip", 1);
    assertStats("block", "gossip_duplicate", 1);

    // duplicate from RPC

    blockBlobSidecarsTrackersPool.onNewBlock(block, Optional.of(RemoteOrigin.RPC));

    assertStats("block", "gossip", 1);
    assertStats("block", "rpc_duplicate", 1);

    final SignedBeaconBlock block2 =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue() + 1);

    // new from RPC
    blockBlobSidecarsTrackersPool.onNewBlock(block2, Optional.of(RemoteOrigin.RPC));

    assertStats("block", "gossip", 1);
    assertStats("block", "rpc", 1);
    assertStats("block", "gossip_duplicate", 1);
    assertStats("block", "rpc_duplicate", 1);

    // no origin is ignored
    final SignedBeaconBlock block3 =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue() + 2);
    blockBlobSidecarsTrackersPool.onNewBlock(block2, Optional.empty());
    blockBlobSidecarsTrackersPool.onNewBlock(block3, Optional.empty());

    assertStats("block", "gossip", 1);
    assertStats("block", "rpc", 1);
    assertStats("block", "gossip_duplicate", 1);
    assertStats("block", "rpc_duplicate", 1);

    // should count even if tracker is already present but without block
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(currentSlot);
    final BlobSidecar blobSidecar4 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(block4.asHeader())
            .build();

    blockBlobSidecarsTrackersPool.onNewBlobSidecar(blobSidecar4, RemoteOrigin.RPC);

    blockBlobSidecarsTrackersPool.onNewBlock(block4, Optional.of(RemoteOrigin.GOSSIP));

    assertStats("block", "gossip", 2);
  }

  private Checkpoint finalizedCheckpoint(SignedBeaconBlock block) {
    final UInt64 epoch = spec.computeEpochAtSlot(block.getSlot()).plus(UInt64.ONE);
    final Bytes32 root = block.getMessage().hashTreeRoot();

    return new Checkpoint(epoch, root);
  }

  private static BlobIdentifier blobIdentifierFromBlobSidecar(final BlobSidecar blobSidecar) {
    return new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
  }

  private void assertStats(final String type, final String subType, final double count) {
    assertThat(getMetricsValues("block_blobs_trackers_pool_stats").get(List.of(type, subType)))
        .isEqualTo(count);
  }

  private void assertBlobSidecarsCount(final int count) {
    assertThat(blockBlobSidecarsTrackersPool.getTotalBlobSidecars()).isEqualTo(count);
    assertThat(
            getMetricsValues("block_blobs_trackers_pool_size")
                .get(List.of(GAUGE_BLOB_SIDECARS_LABEL)))
        .isEqualTo((double) count);
  }

  private void assertBlobSidecarsTrackersCount(final int count) {
    assertThat(blockBlobSidecarsTrackersPool.getTotalBlobSidecarsTrackers()).isEqualTo(count);
    assertThat(
            getMetricsValues("block_blobs_trackers_pool_size")
                .get(List.of(GAUGE_BLOB_SIDECARS_TRACKERS_LABEL)))
        .isEqualTo((double) count);
  }

  private Map<List<String>, Object> getMetricsValues(final String metricName) {
    return metricsSystem
        .streamObservations(TekuMetricCategory.BEACON)
        .filter(ob -> ob.getMetricName().equals(metricName))
        .collect(Collectors.toMap(Observation::getLabels, Observation::getValue));
  }

  private BlockBlobSidecarsTracker trackerFactory(final SlotAndBlockRoot slotAndBlockRoot) {
    if (mockedTrackersFactory.isPresent()) {
      return mockedTrackersFactory.get().apply(slotAndBlockRoot);
    }
    return new BlockBlobSidecarsTracker(
        slotAndBlockRoot, UInt64.valueOf(spec.getMaxBlobsPerBlock().orElseThrow()));
  }
}
