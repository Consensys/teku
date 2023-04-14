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

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarPool;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarPoolImpl extends AbstractIgnoringFutureHistoricalSlot
    implements BlobSidecarPool {

  static final String GAUGE_BLOB_SIDECARS_LABEL = "blob_sidecars";
  static final String GAUGE_BLOB_SIDECARS_TRACKERS_LABEL = "blob_sidecars_trackers";

  private static final UInt64 MAX_WAIT_RELATIVE_TO_ATT_DUE = UInt64.valueOf(1500);
  private static final UInt64 MIN_WAIT_MILLIS = UInt64.valueOf(500);
  private static final UInt64 TARGET_WAIT_MILLIS = UInt64.valueOf(1000);

  private final SettableLabelledGauge sizeGauge;
  private final Map<Bytes32, BlockBlobSidecarsTracker> blockBlobSidecarsTrackers = new HashMap<>();
  private final NavigableSet<SlotAndBlockRoot> orderedBlobSidecarsTrackers = new TreeSet<>();
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final int maxTrackers;

  private final Subscribers<RequiredBlockRootSubscriber> requiredBlockRootSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlockRootDroppedSubscriber>
      requiredBlockRootDroppedSubscribers = Subscribers.create(true);

  private final Subscribers<RequiredBlobSidecarSubscriber> requiredBlobSidecarSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlobSidecarDroppedSubscriber>
      requiredBlobSidecarDroppedSubscribers = Subscribers.create(true);

  private int totalBlobSidecars;

  BlobSidecarPoolImpl(
      final SettableLabelledGauge sizeGauge,
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxTrackers) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.maxTrackers = maxTrackers;
    this.sizeGauge = sizeGauge;

    // Init the label so it appears in metrics immediately
    sizeGauge.set(0, GAUGE_BLOB_SIDECARS_LABEL);
    sizeGauge.set(0, GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
  }

  @Override
  public synchronized void onNewBlobSidecar(final BlobSidecar blobSidecar, final boolean syncing) {
    if (shouldIgnoreItemAtSlot(blobSidecar.getSlot())) {
      return;
    }

    makeRoomForNewTracker();

    final SlotAndBlockRoot slotAndBlockRoot = blobSidecar.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blobSidecars =
        getOrCreateBlobSidecarsTracker(
            slotAndBlockRoot,
            newTracker -> {
              newTracker.add(blobSidecar);

              sizeGauge.set(++totalBlobSidecars, GAUGE_BLOB_SIDECARS_LABEL);

              if (!syncing) {
                onFirstSeen(slotAndBlockRoot);
              }
            });

    if (orderedBlobSidecarsTrackers.add(blobSidecars.getSlotAndBlockRoot())) {
      sizeGauge.set(orderedBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  @Override
  public synchronized void onNewBlock(final SignedBeaconBlock block) {
    if (shouldIgnoreItemAtSlot(block.getSlot())) {
      return;
    }

    makeRoomForNewTracker();

    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blobSidecars =
        getOrCreateBlobSidecarsTracker(
            slotAndBlockRoot,
            newTracker -> {
              newTracker.setBlock(block);
              onFirstSeen(slotAndBlockRoot);
            });

    if (orderedBlobSidecarsTrackers.add(blobSidecars.getSlotAndBlockRoot())) {
      sizeGauge.set(orderedBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  public synchronized void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {
    orderedBlobSidecarsTrackers.remove(slotAndBlockRoot);
    final BlockBlobSidecarsTracker removedTracker =
        blockBlobSidecarsTrackers.remove(slotAndBlockRoot.getBlockRoot());

    if (removedTracker != null) {

      dropMissingContent(removedTracker);

      totalBlobSidecars -= removedTracker.blobSidecarsCount();
      sizeGauge.set(totalBlobSidecars, GAUGE_BLOB_SIDECARS_LABEL);
      sizeGauge.set(blockBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  private void makeRoomForNewTracker() {
    while (blockBlobSidecarsTrackers.size() > (maxTrackers - 1)) {
      final SlotAndBlockRoot toRemove = orderedBlobSidecarsTrackers.pollFirst();
      if (toRemove == null) {
        break;
      }
      removeAllForBlock(toRemove);
    }
  }

  @Override
  public synchronized BlockBlobSidecarsTracker getBlockBlobsSidecarsTracker(
      final SignedBeaconBlock block) {
    return getOrCreateBlobSidecarsTracker(block.getSlotAndBlockRoot(), __ -> {});
  }

  @VisibleForTesting
  @Override
  synchronized void prune(final UInt64 slotLimit) {
    final List<SlotAndBlockRoot> toRemove = new ArrayList<>();
    for (SlotAndBlockRoot slotAndBlockRoot : orderedBlobSidecarsTrackers) {
      if (slotAndBlockRoot.getSlot().isGreaterThan(slotLimit)) {
        break;
      }
      toRemove.add(slotAndBlockRoot);
    }

    toRemove.forEach(this::removeAllForBlock);
  }

  @Override
  public synchronized boolean containsBlobSidecar(final BlobIdentifier blobIdentifier) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(blobIdentifier.getBlockRoot()))
        .map(tracker -> tracker.containsBlobSidecar(blobIdentifier))
        .orElse(false);
  }

  public synchronized boolean containsBlock(final SignedBeaconBlock block) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(block.getRoot()))
        .map(tracker -> tracker.getBlockBody().isPresent())
        .orElse(false);
  }

  @Override
  public synchronized Set<BlobIdentifier> getAllRequiredBlobSidecars() {
    return blockBlobSidecarsTrackers.values().stream()
        .flatMap(BlockBlobSidecarsTracker::getMissingBlobSidecars)
        .collect(Collectors.toSet());
  }

  @Override
  public void subscribeRequiredBlobSidecar(
      final RequiredBlobSidecarSubscriber requiredBlobSidecarSubscriber) {
    requiredBlobSidecarSubscribers.subscribe(requiredBlobSidecarSubscriber);
  }

  @Override
  public void subscribeRequiredBlobSidecarDropped(
      final RequiredBlobSidecarDroppedSubscriber requiredBlobSidecarDroppedSubscriber) {
    requiredBlobSidecarDroppedSubscribers.subscribe(requiredBlobSidecarDroppedSubscriber);
  }

  @Override
  public void subscribeRequiredBlockRoot(
      final RequiredBlockRootSubscriber requiredBlockRootSubscriber) {
    requiredBlockRootSubscribers.subscribe(requiredBlockRootSubscriber);
  }

  @Override
  public void subscribeRequiredBlockRootDropped(
      final RequiredBlockRootDroppedSubscriber requiredBlockRootDroppedSubscriber) {
    requiredBlockRootDroppedSubscribers.subscribe(requiredBlockRootDroppedSubscriber);
  }

  public synchronized int getTotalBlobSidecars() {
    return totalBlobSidecars;
  }

  public synchronized int getTotalBlobSidecarsTrackers() {
    return blockBlobSidecarsTrackers.size();
  }

  private BlockBlobSidecarsTracker getOrCreateBlobSidecarsTracker(
      final SlotAndBlockRoot slotAndBlockRoot, Consumer<BlockBlobSidecarsTracker> onNew) {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());
    if (blockBlobSidecarsTracker == null) {
      blockBlobSidecarsTracker = new BlockBlobSidecarsTracker(slotAndBlockRoot);
      blockBlobSidecarsTrackers.put(slotAndBlockRoot.getBlockRoot(), blockBlobSidecarsTracker);
      onNew.accept(blockBlobSidecarsTracker);
    }
    return blockBlobSidecarsTracker;
  }

  private void onFirstSeen(final SlotAndBlockRoot slotAndBlockRoot) {
    final Optional<Duration> fetchDelay = calculateFetchDelay(slotAndBlockRoot);

    if (fetchDelay.isEmpty()) {
      return;
    }

    asyncRunner
        .runAfterDelay(() -> this.fetchMissingContent(slotAndBlockRoot), fetchDelay.get())
        .ifExceptionGetsHereRaiseABug();
  }

  private Optional<Duration> calculateFetchDelay(final SlotAndBlockRoot slotAndBlockRoot) {
    final UInt64 slot = slotAndBlockRoot.getSlot();

    if (slot.isLessThan(getCurrentSlot())) {
      // old slot
      return Optional.empty();
    }

    final UInt64 nowMillis = timeProvider.getTimeInMillis();
    final UInt64 slotStartTimeMillis = secondsToMillis(recentChainData.computeTimeAtSlot(slot));
    final UInt64 millisPerSlot = secondsToMillis(spec.getSecondsPerSlot(slot));
    final UInt64 attestationDueMillis = slotStartTimeMillis.plus(millisPerSlot.dividedBy(3));

    if (nowMillis.isGreaterThanOrEqualTo(attestationDueMillis)) {
      return Optional.of(Duration.ofMillis(TARGET_WAIT_MILLIS.intValue()));
    }

    final UInt64 upperLimitRelativeToAttDue =
        attestationDueMillis.minus(MAX_WAIT_RELATIVE_TO_ATT_DUE);

    final UInt64 targetMillis = nowMillis.plus(TARGET_WAIT_MILLIS);

    final UInt64 finalTime =
        targetMillis.min(upperLimitRelativeToAttDue).max(nowMillis.plus(MIN_WAIT_MILLIS));

    return Optional.of(Duration.ofMillis(finalTime.minus(nowMillis).intValue()));
  }

  private synchronized void fetchMissingContent(final SlotAndBlockRoot slotAndBlockRoot) {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());

    if (blockBlobSidecarsTracker == null) {
      return;
    }

    if (blockBlobSidecarsTracker.checkCompletion()) {
      return;
    }

    if (blockBlobSidecarsTracker.getBlockBody().isEmpty()) {
      requiredBlockRootSubscribers.deliver(
          RequiredBlockRootSubscriber::onRequiredBlockRoot,
          blockBlobSidecarsTracker.getSlotAndBlockRoot().getBlockRoot());
      return;
    }

    blockBlobSidecarsTracker
        .getMissingBlobSidecars()
        .forEach(
            blobIdentifier ->
                requiredBlobSidecarSubscribers.deliver(
                    RequiredBlobSidecarSubscriber::onRequiredBlobSidecar, blobIdentifier));
  }

  private void dropMissingContent(final BlockBlobSidecarsTracker blockBlobSidecarsTracker) {

    if (blockBlobSidecarsTracker.getBlockBody().isEmpty()) {
      requiredBlockRootDroppedSubscribers.deliver(
          RequiredBlockRootDroppedSubscriber::onRequiredBlockRootDropped,
          blockBlobSidecarsTracker.getSlotAndBlockRoot().getBlockRoot());
      return;
    }

    blockBlobSidecarsTracker
        .getMissingBlobSidecars()
        .forEach(
            blobIdentifier ->
                requiredBlobSidecarDroppedSubscribers.deliver(
                    RequiredBlobSidecarDroppedSubscriber::onRequiredBlobSidecarDropped,
                    blobIdentifier));
  }
}
