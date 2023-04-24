/*
 * Copyright ConsenSys Software Inc., 2023
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
import static tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl.GAUGE_BLOB_SIDECARS_LABEL;
import static tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl.GAUGE_BLOB_SIDECARS_TRACKERS_LABEL;
import static tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl.MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS;
import static tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl.MIN_WAIT_MILLIS;
import static tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl.TARGET_WAIT_MILLIS;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarPoolImplTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 historicalTolerance = UInt64.valueOf(5);
  private final UInt64 futureTolerance = UInt64.valueOf(2);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final int maxItems = 15;
  private final BlobSidecarPoolImpl blobSidecarPool =
      new PoolFactory(metricsSystem)
          .createPoolForBlobSidecars(
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

  private Optional<Function<SlotAndBlockRoot, BlockBlobSidecarsTracker>> mockedTrackersFactory =
      Optional.empty();

  @BeforeEach
  public void setup() {
    // Set up slot
    blobSidecarPool.subscribeRequiredBlockRoot(requiredBlockRootEvents::add);
    blobSidecarPool.subscribeRequiredBlockRootDropped(requiredBlockRootDroppedEvents::add);
    blobSidecarPool.subscribeRequiredBlobSidecar(requiredBlobSidecarEvents::add);
    blobSidecarPool.subscribeRequiredBlobSidecarDropped(requiredBlobSidecarDroppedEvents::add);
    setSlot(currentSlot);
  }

  private void setSlot(final long slot) {
    setSlot(UInt64.valueOf(slot));
  }

  private void setSlot(final UInt64 slot) {
    currentSlot = slot;
    blobSidecarPool.onSlot(slot);
    when(recentChainData.computeTimeAtSlot(any())).thenReturn(UInt64.ZERO);
  }

  @Test
  public void onNewBlock_addTrackerWithBlock() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    blobSidecarPool.onNewBlock(block);

    assertThat(blobSidecarPool.containsBlock(block)).isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void onNewBlobSidecar_addTrackerWithBlobSidecar() {
    final BlobSidecar blobSidecar =
        dataStructureUtil.createRandomBlobSidecarBuilder().slot(currentSlot).build();

    blobSidecarPool.onNewBlobSidecar(blobSidecar);

    assertThat(blobSidecarPool.containsBlobSidecar(blobIdentifierFromBlobSidecar(blobSidecar)))
        .isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();

    assertBlobSidecarsCount(1);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void onNewBlobSidecarOnNewBlock_addTrackerWithBothBlockAndBlobSidecar() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(currentSlot)
            .blockRoot(block.getRoot())
            .build();

    blobSidecarPool.onNewBlobSidecar(blobSidecar);
    blobSidecarPool.onNewBlock(block);

    assertThat(blobSidecarPool.containsBlobSidecar(blobIdentifierFromBlobSidecar(blobSidecar)))
        .isTrue();
    assertThat(blobSidecarPool.containsBlock(block)).isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();

    assertBlobSidecarsCount(1);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void twoOnNewBlobSidecar_addTrackerWithBothBlobSidecars() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

    final BlobSidecar blobSidecar0 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(currentSlot)
            .blockRoot(blockRoot)
            .index(UInt64.ZERO)
            .build();

    final BlobSidecar blobSidecar1 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(currentSlot)
            .blockRoot(blockRoot)
            .index(UInt64.ONE)
            .build();

    final BlobSidecar blobSidecar1bis =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(currentSlot)
            .blockRoot(blockRoot)
            .index(UInt64.ONE)
            .build();

    blobSidecarPool.onNewBlobSidecar(blobSidecar0);
    blobSidecarPool.onNewBlobSidecar(blobSidecar1);
    blobSidecarPool.onNewBlobSidecar(blobSidecar1bis);

    assertThat(blobSidecarPool.containsBlobSidecar(blobIdentifierFromBlobSidecar(blobSidecar0)))
        .isTrue();
    assertThat(blobSidecarPool.containsBlobSidecar(blobIdentifierFromBlobSidecar(blobSidecar1)))
        .isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();

    assertBlobSidecarsCount(2);
    assertBlobSidecarsTrackersCount(1);
  }

  @Test
  public void twoOnNewBlock_addTrackerWithBothBlobSidecars() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final SignedBeaconBlock blockAtPreviousSlot =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue() - 1);

    blobSidecarPool.onNewBlock(blockAtPreviousSlot);
    blobSidecarPool.onNewBlock(block);

    assertThat(blobSidecarPool.containsBlock(blockAtPreviousSlot)).isTrue();
    assertThat(blobSidecarPool.containsBlock(block)).isTrue();
    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();

    assertBlobSidecarsCount(0);
    assertBlobSidecarsTrackersCount(2);
  }

  @Test
  public void removeAllForBlock_shouldRemoveSidecarsAndTracker() {}

  @Test
  public void shouldApplyIgnoreForBlock() {
    final UInt64 slot = currentSlot.plus(futureTolerance).plus(UInt64.ONE);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());

    blobSidecarPool.onNewBlock(block);

    assertThat(blobSidecarPool.containsBlock(block)).isFalse();
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
        dataStructureUtil.createRandomBlobSidecarBuilder().slot(slot).build();

    blobSidecarPool.onNewBlobSidecar(blobSidecar);

    assertThat(blobSidecarPool.containsBlobSidecar(blobIdentifierFromBlobSidecar(blobSidecar)))
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
      blobSidecarPool.onNewBlock(block);

      final int expectedSize = Math.min(maxItems, i + 1);
      assertThat(blobSidecarPool.containsBlock(block)).isTrue();
      assertThat(blobSidecarPool.getTotalBlobSidecarsTrackers()).isEqualTo(expectedSize);
      assertBlobSidecarsTrackersCount(expectedSize);
    }

    // Final sanity check
    assertThat(blobSidecarPool.getTotalBlobSidecarsTrackers()).isEqualTo(maxItems);
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
    nonFinalBlocks.forEach(blobSidecarPool::onNewBlock);
    finalizedBlocks.forEach(blobSidecarPool::onNewBlock);

    // Check that all blocks are in the collection
    assertBlobSidecarsTrackersCount(finalizedBlocks.size() + nonFinalBlocks.size());
    for (SignedBeaconBlock block : allBlocks) {
      assertThat(blobSidecarPool.containsBlock(block)).isTrue();
    }

    // Update finalized checkpoint and prune
    blobSidecarPool.onNewFinalizedCheckpoint(checkpoint, false);
    blobSidecarPool.prune();

    // Check that all final blocks have been pruned
    assertBlobSidecarsTrackersCount(nonFinalBlocks.size());
    for (SignedBeaconBlock block : nonFinalBlocks) {
      assertThat(blobSidecarPool.containsBlock(block)).isTrue();
    }
  }

  @Test
  void shouldFetchMissingBlobSidecars() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final Set<BlobIdentifier> missingBlobs =
        Set.of(
            new BlobIdentifier(block.getRoot(), UInt64.ONE),
            new BlobIdentifier(block.getRoot(), UInt64.ZERO));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getMissingBlobSidecars()).thenReturn(missingBlobs.stream());
              when(tracker.getBlockBody())
                  .thenReturn(Optional.of((BeaconBlockBodyDeneb) block.getMessage().getBody()));
              return tracker;
            });

    blobSidecarPool.onNewBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();

    assertThat(requiredBlockRootEvents).isEmpty();
    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarEvents).containsExactlyElementsOf(missingBlobs);
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
  }

  @Test
  void shouldFetchMissingBlockAndBlobSidecars() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot, dataStructureUtil.randomBytes32());
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .blockRoot(slotAndBlockRoot.getBlockRoot())
            .index(UInt64.valueOf(2))
            .slot(currentSlot)
            .build();

    final Set<BlobIdentifier> missingBlobs =
        Set.of(
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.ONE),
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.ZERO));

    final BlockBlobSidecarsTracker mockedTracker = mock(BlockBlobSidecarsTracker.class);
    when(mockedTracker.getBlockBody()).thenReturn(Optional.empty());
    when(mockedTracker.getMissingBlobSidecars()).thenReturn(missingBlobs.stream());
    when(mockedTracker.getSlotAndBlockRoot()).thenReturn(slotAndBlockRoot);

    mockedTrackersFactory = Optional.of((__) -> mockedTracker);

    blobSidecarPool.onNewBlobSidecar(blobSidecar);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();

    verify(mockedTracker).setFetchTriggered();

    assertThat(requiredBlockRootEvents).containsExactly(slotAndBlockRoot.getBlockRoot());
    assertThat(requiredBlobSidecarEvents).containsExactlyElementsOf(missingBlobs);

    assertThat(requiredBlockRootDroppedEvents).isEmpty();
    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
  }

  @Test
  void shouldDropBlobSidecarsThatHasBeenFetchedButNotPresentInBlock() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(currentSlot, block.getRoot());
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .blockRoot(slotAndBlockRoot.getBlockRoot())
            .index(UInt64.valueOf(2))
            .slot(currentSlot)
            .build();

    final Set<BlobIdentifier> blobsNotUserInBlock =
        Set.of(
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(2)),
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(3)));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getBlockBody()).thenReturn(Optional.empty());
              when(tracker.getSlotAndBlockRoot()).thenReturn(slotAndBlockRoot);
              when(tracker.setBlock(block)).thenReturn(true);
              when(tracker.isFetchTriggered()).thenReturn(true);
              when(tracker.getUnusedBlobSidecarsForBlock())
                  .thenReturn(blobsNotUserInBlock.stream());
              return tracker;
            });

    blobSidecarPool.onNewBlobSidecar(blobSidecar);

    blobSidecarPool.onNewBlock(block);

    assertThat(requiredBlobSidecarDroppedEvents).containsExactlyElementsOf(blobsNotUserInBlock);
  }

  @Test
  void shouldNotDropUnusedBlobSidecarsIfFetchingHasNotOccurred() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(currentSlot, block.getRoot());
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .blockRoot(slotAndBlockRoot.getBlockRoot())
            .index(UInt64.valueOf(2))
            .slot(currentSlot)
            .build();

    final Set<BlobIdentifier> blobsNotUserInBlock =
        Set.of(
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(2)),
            new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(3)));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getBlockBody()).thenReturn(Optional.empty());
              when(tracker.getSlotAndBlockRoot()).thenReturn(slotAndBlockRoot);
              when(tracker.setBlock(block)).thenReturn(true);
              when(tracker.isFetchTriggered()).thenReturn(false);
              when(tracker.getUnusedBlobSidecarsForBlock())
                  .thenReturn(blobsNotUserInBlock.stream());
              return tracker;
            });

    blobSidecarPool.onNewBlobSidecar(blobSidecar);

    blobSidecarPool.onNewBlock(block);

    assertThat(requiredBlobSidecarDroppedEvents).isEmpty();
  }

  @Test
  public void shouldNotFetchContentWhenBlockIsNotForCurrentSlot() {
    final UInt64 slot = currentSlot.minus(UInt64.ONE);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot.longValue());

    blobSidecarPool.onNewBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldNotFetchContentWhenBlobSidecarIsNotForCurrentSlot() {
    final UInt64 slot = currentSlot.minus(UInt64.ONE);
    final BlobSidecar blobSidecar =
        dataStructureUtil.createRandomBlobSidecarBuilder().slot(slot).build();

    blobSidecarPool.onNewBlobSidecar(blobSidecar);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  void shouldDropPossiblyFetchedBlobSidecars() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final Set<BlobIdentifier> missingBlobs =
        Set.of(
            new BlobIdentifier(block.getRoot(), UInt64.ONE),
            new BlobIdentifier(block.getRoot(), UInt64.ZERO));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getMissingBlobSidecars()).thenReturn(missingBlobs.stream());
              when(tracker.getBlockBody())
                  .thenReturn(Optional.of((BeaconBlockBodyDeneb) block.getMessage().getBody()));
              return tracker;
            });

    blobSidecarPool.onNewBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    blobSidecarPool.removeAllForBlock(block.getSlotAndBlockRoot());

    assertThat(requiredBlobSidecarDroppedEvents).containsExactlyElementsOf(missingBlobs);

    // subsequent fetch will not try to fetch anything
    asyncRunner.executeQueuedActions();

    assertThat(requiredBlobSidecarEvents).isEmpty();
  }

  @Test
  void shouldDropPossiblyFetchedBlock() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot, dataStructureUtil.randomBytes32());
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .blockRoot(slotAndBlockRoot.getBlockRoot())
            .slot(currentSlot)
            .build();

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getBlockBody()).thenReturn(Optional.empty());
              when(tracker.getSlotAndBlockRoot()).thenReturn(slotAndBlockRoot);
              return tracker;
            });

    blobSidecarPool.onNewBlobSidecar(blobSidecar);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    blobSidecarPool.removeAllForBlock(slotAndBlockRoot);

    assertThat(requiredBlockRootDroppedEvents).containsExactly(slotAndBlockRoot.getBlockRoot());

    // subsequent fetch will not try to fetch anything
    asyncRunner.executeQueuedActions();

    assertThat(requiredBlockRootEvents).isEmpty();
  }

  @Test
  void shouldRespectTargetWhenBlockIsEarly() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot, dataStructureUtil.randomBytes32());

    final UInt64 startSlotInSeconds = UInt64.valueOf(10);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    // blocks arrives at slot start
    timeProvider.advanceTimeBySeconds(startSlotInSeconds.longValue());

    final Optional<Duration> fetchDelay = blobSidecarPool.calculateFetchDelay(slotAndBlockRoot);

    // we can wait the full target
    assertThat(fetchDelay)
        .isEqualTo(Optional.of(Duration.ofMillis(TARGET_WAIT_MILLIS.longValue())));
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

    final Optional<Duration> fetchDelay = blobSidecarPool.calculateFetchDelay(slotAndBlockRoot);

    // we can wait the full target
    assertThat(fetchDelay).isEqualTo(Optional.of(Duration.ofMillis(MIN_WAIT_MILLIS.longValue())));
  }

  @Test
  void calculateFetchDelay_shouldRespectTargetWhenBlockIsVeryLate() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot, dataStructureUtil.randomBytes32());

    final UInt64 startSlotInSeconds = UInt64.valueOf(10);

    when(recentChainData.computeTimeAtSlot(currentSlot)).thenReturn(startSlotInSeconds);

    // blocks arrives 1s after attestation due
    timeProvider.advanceTimeBySeconds(startSlotInSeconds.plus(5).longValue());

    final Optional<Duration> fetchDelay = blobSidecarPool.calculateFetchDelay(slotAndBlockRoot);

    // we can wait the full target
    assertThat(fetchDelay)
        .isEqualTo(Optional.of(Duration.ofMillis(TARGET_WAIT_MILLIS.longValue())));
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

    final Optional<Duration> fetchDelay = blobSidecarPool.calculateFetchDelay(slotAndBlockRoot);

    // we can only wait 200ms less than target
    assertThat(fetchDelay)
        .isEqualTo(
            Optional.of(
                Duration.ofMillis(
                    TARGET_WAIT_MILLIS.minus(millisecondsIntoAttDueLimit).longValue())));
  }

  @Test
  void calculateFetchDelay_shouldReturnEmptyIfSlotIsOld() {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(currentSlot.minus(1), dataStructureUtil.randomBytes32());

    final Optional<Duration> fetchDelay = blobSidecarPool.calculateFetchDelay(slotAndBlockRoot);

    // we can only wait 200ms less than target
    assertThat(fetchDelay).isEqualTo(Optional.empty());
  }

  @Test
  void getAllRequiredBlobSidecars_shouldReturnAllRequiredBlobsSidecars() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final Set<BlobIdentifier> missingBlobs1 =
        Set.of(
            new BlobIdentifier(block1.getRoot(), UInt64.ONE),
            new BlobIdentifier(block1.getRoot(), UInt64.ZERO));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getMissingBlobSidecars()).thenReturn(missingBlobs1.stream());
              when(tracker.getBlockBody())
                  .thenReturn(Optional.of((BeaconBlockBodyDeneb) block1.getMessage().getBody()));
              return tracker;
            });

    blobSidecarPool.onNewBlock(block1);

    final SignedBeaconBlock block2 =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());

    final Set<BlobIdentifier> missingBlobs2 =
        Set.of(
            new BlobIdentifier(block2.getRoot(), UInt64.ONE),
            new BlobIdentifier(block2.getRoot(), UInt64.valueOf(2)));

    mockedTrackersFactory =
        Optional.of(
            (slotAndRoot) -> {
              BlockBlobSidecarsTracker tracker = mock(BlockBlobSidecarsTracker.class);
              when(tracker.getMissingBlobSidecars()).thenReturn(missingBlobs2.stream());
              when(tracker.getBlockBody())
                  .thenReturn(Optional.of((BeaconBlockBodyDeneb) block2.getMessage().getBody()));
              return tracker;
            });

    blobSidecarPool.onNewBlock(block2);

    final Set<BlobIdentifier> allMissing =
        Stream.concat(missingBlobs1.stream(), missingBlobs2.stream()).collect(Collectors.toSet());

    assertThat(blobSidecarPool.getAllRequiredBlobSidecars()).containsExactlyElementsOf(allMissing);
  }

  private Checkpoint finalizedCheckpoint(SignedBeaconBlock block) {
    final UInt64 epoch = spec.computeEpochAtSlot(block.getSlot()).plus(UInt64.ONE);
    final Bytes32 root = block.getMessage().hashTreeRoot();

    return new Checkpoint(epoch, root);
  }

  private static BlobIdentifier blobIdentifierFromBlobSidecar(final BlobSidecar blobSidecar) {
    return new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
  }

  private void assertBlobSidecarsCount(final int count) {
    assertThat(blobSidecarPool.getTotalBlobSidecars()).isEqualTo(count);
    assertThat(
            metricsSystem
                .getLabelledGauge(TekuMetricCategory.BEACON, "pending_pool_size")
                .getValue(GAUGE_BLOB_SIDECARS_LABEL))
        .isEqualTo(OptionalDouble.of(count));
  }

  private void assertBlobSidecarsTrackersCount(final int count) {
    assertThat(blobSidecarPool.getTotalBlobSidecarsTrackers()).isEqualTo(count);
    assertThat(
            metricsSystem
                .getLabelledGauge(TekuMetricCategory.BEACON, "pending_pool_size")
                .getValue(GAUGE_BLOB_SIDECARS_TRACKERS_LABEL))
        .isEqualTo(OptionalDouble.of(count));
  }

  private BlockBlobSidecarsTracker trackerFactory(final SlotAndBlockRoot slotAndBlockRoot) {
    if (mockedTrackersFactory.isPresent()) {
      return mockedTrackersFactory.get().apply(slotAndBlockRoot);
    }
    return new BlockBlobSidecarsTracker(
        slotAndBlockRoot, UInt64.valueOf(spec.getMaxBlobsPerBlock().orElseThrow()));
  }
}
