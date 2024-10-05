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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.RemoteOrigin.LOCAL_EL;

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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndProof;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager.RemoteOrigin;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackerFactory;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockBlobSidecarsTrackersPoolImpl extends AbstractIgnoringFutureHistoricalSlot
    implements BlockBlobSidecarsTrackersPool {
  private static final Logger LOG = LogManager.getLogger();

  static final String COUNTER_BLOCK_TYPE = "block";
  static final String COUNTER_SIDECAR_TYPE = "blob_sidecar";

  static final String COUNTER_GOSSIP_SUBTYPE = "gossip";
  static final String COUNTER_LOCAL_EL_SUBTYPE = "local_el";
  static final String COUNTER_RPC_SUBTYPE = "rpc";
  static final String COUNTER_GOSSIP_DUPLICATE_SUBTYPE = "gossip_duplicate";
  static final String COUNTER_RPC_DUPLICATE_SUBTYPE = "rpc_duplicate";
  static final String COUNTER_LOCAL_EL_DUPLICATE_SUBTYPE = "local_el_duplicate";

  static final String COUNTER_RPC_FETCH_SUBTYPE = "rpc_fetch";
  static final String COUNTER_LOCAL_EL_FETCH_SUBTYPE = "local_el_fetch";

  static final String GAUGE_BLOB_SIDECARS_LABEL = "blob_sidecars";
  static final String GAUGE_BLOB_SIDECARS_TRACKERS_LABEL = "blob_sidecars_trackers";

  static final UInt64 MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS = UInt64.valueOf(1500);
  static final UInt64 MIN_WAIT_MILLIS = UInt64.valueOf(500);
  static final UInt64 TARGET_WAIT_MILLIS = UInt64.valueOf(1000);

  private final SettableLabelledGauge sizeGauge;
  private final LabelledMetric<Counter> poolStatsCounters;
  private final Map<Bytes32, BlockBlobSidecarsTracker> blockBlobSidecarsTrackers = new HashMap<>();
  private final NavigableSet<SlotAndBlockRoot> orderedBlobSidecarsTrackers = new TreeSet<>();
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final ExecutionLayerChannel executionLayer;
  private final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier;
  private final Consumer<BlobSidecar> blobSidecarGossipPublisher;
  private final int maxTrackers;

  private final BlockBlobSidecarsTrackerFactory trackerFactory;

  private final Subscribers<RequiredBlockRootSubscriber> requiredBlockRootSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlockRootDroppedSubscriber>
      requiredBlockRootDroppedSubscribers = Subscribers.create(true);

  private final Subscribers<RequiredBlobSidecarSubscriber> requiredBlobSidecarSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlobSidecarDroppedSubscriber>
      requiredBlobSidecarDroppedSubscribers = Subscribers.create(true);

  private final Subscribers<NewBlobSidecarSubscriber> newBlobSidecarSubscribers =
      Subscribers.create(true);

  private int totalBlobSidecars;

  private final BlockImportChannel blockImportChannel;

  BlockBlobSidecarsTrackersPoolImpl(
      final BlockImportChannel blockImportChannel,
      final SettableLabelledGauge sizeGauge,
      final LabelledMetric<Counter> poolStatsCounters,
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Consumer<BlobSidecar> blobSidecarGossipPublisher,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxTrackers) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    this.blockImportChannel = blockImportChannel;
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.executionLayer = executionLayer;
    this.gossipValidatorSupplier = gossipValidatorSupplier;
    this.blobSidecarGossipPublisher = blobSidecarGossipPublisher;
    this.maxTrackers = maxTrackers;
    this.sizeGauge = sizeGauge;
    this.poolStatsCounters = poolStatsCounters;
    this.trackerFactory = (slotAndBlockRoot) -> createTracker(spec, slotAndBlockRoot);

    initMetrics(sizeGauge, poolStatsCounters);
  }

  @VisibleForTesting
  BlockBlobSidecarsTrackersPoolImpl(
      final BlockImportChannel blockImportChannel,
      final SettableLabelledGauge sizeGauge,
      final LabelledMetric<Counter> poolStatsCounters,
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Consumer<BlobSidecar> blobSidecarGossipPublisher,
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxTrackers,
      final BlockBlobSidecarsTrackerFactory trackerFactory) {
    super(spec, futureSlotTolerance, historicalSlotTolerance);
    this.blockImportChannel = blockImportChannel;
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.executionLayer = executionLayer;
    this.gossipValidatorSupplier = gossipValidatorSupplier;
    this.blobSidecarGossipPublisher = blobSidecarGossipPublisher;
    this.maxTrackers = maxTrackers;
    this.sizeGauge = sizeGauge;
    this.poolStatsCounters = poolStatsCounters;
    this.trackerFactory = trackerFactory;

    initMetrics(sizeGauge, poolStatsCounters);
  }

  private static void initMetrics(
      final SettableLabelledGauge sizeGauge, final LabelledMetric<Counter> poolStatsCounters) {
    // Init the label so it appears in metrics immediately
    sizeGauge.set(0, GAUGE_BLOB_SIDECARS_LABEL);
    sizeGauge.set(0, GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);

    Stream.of(COUNTER_BLOCK_TYPE, COUNTER_SIDECAR_TYPE)
        .forEach(
            type -> {
              poolStatsCounters.labels(type, COUNTER_GOSSIP_SUBTYPE);
              poolStatsCounters.labels(type, COUNTER_RPC_SUBTYPE);
              poolStatsCounters.labels(type, COUNTER_GOSSIP_DUPLICATE_SUBTYPE);
              poolStatsCounters.labels(type, COUNTER_RPC_DUPLICATE_SUBTYPE);
              poolStatsCounters.labels(type, COUNTER_RPC_FETCH_SUBTYPE);
            });
  }

  private static BlockBlobSidecarsTracker createTracker(
      final Spec spec, final SlotAndBlockRoot slotAndBlockRoot) {
    return new BlockBlobSidecarsTracker(
        slotAndBlockRoot,
        UInt64.valueOf(spec.getMaxBlobsPerBlock(slotAndBlockRoot.getSlot()).orElseThrow()));
  }

  @Override
  public synchronized void onNewBlobSidecar(
      final BlobSidecar blobSidecar, final RemoteOrigin remoteOrigin) {
    if (recentChainData.containsBlock(blobSidecar.getBlockRoot())) {
      return;
    }
    if (shouldIgnoreItemAtSlot(blobSidecar.getSlot())) {
      return;
    }

    final SlotAndBlockRoot slotAndBlockRoot = blobSidecar.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blobSidecarsTracker =
        getOrCreateBlobSidecarsTracker(
            slotAndBlockRoot, newTracker -> onFirstSeen(slotAndBlockRoot), existingTracker -> {});

    if (blobSidecarsTracker.add(blobSidecar)) {
      sizeGauge.set(++totalBlobSidecars, GAUGE_BLOB_SIDECARS_LABEL);
      countBlobSidecar(remoteOrigin);
      newBlobSidecarSubscribers.deliver(NewBlobSidecarSubscriber::onNewBlobSidecar, blobSidecar);
      if (remoteOrigin.equals(LOCAL_EL) && slotAndBlockRoot.getSlot().equals(getCurrentSlot())) {
        publishRecoveredBlobSidecar(blobSidecar);
      }
    } else {
      countDuplicateBlobSidecar(remoteOrigin);
    }

    if (orderedBlobSidecarsTrackers.add(slotAndBlockRoot)) {
      sizeGauge.set(orderedBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  private void publishRecoveredBlobSidecar(final BlobSidecar blobSidecar) {
    LOG.debug("Publishing recovered blob sidecar {}", blobSidecar::toLogString);
    gossipValidatorSupplier.get().markForEquivocation(blobSidecar);
    blobSidecarGossipPublisher.accept(blobSidecar);
  }

  private void countBlobSidecar(final RemoteOrigin origin) {
    switch (origin) {
      case RPC -> poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_RPC_SUBTYPE).inc();
      case GOSSIP -> poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_GOSSIP_SUBTYPE).inc();
      case LOCAL_EL ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_LOCAL_EL_SUBTYPE).inc();
    }
  }

  private void countDuplicateBlobSidecar(final RemoteOrigin origin) {
    switch (origin) {
      case RPC ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_RPC_DUPLICATE_SUBTYPE).inc();
      case GOSSIP ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_GOSSIP_DUPLICATE_SUBTYPE).inc();
      case LOCAL_EL ->
          poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_LOCAL_EL_DUPLICATE_SUBTYPE).inc();
    }
  }

  @Override
  public synchronized void onNewBlock(
      final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    if (block.getMessage().getBody().toVersionDeneb().isEmpty()) {
      return;
    }
    if (recentChainData.containsBlock(block.getRoot())) {
      return;
    }
    if (shouldIgnoreItemAtSlot(block.getSlot())) {
      return;
    }
    internalOnNewBlock(block, remoteOrigin);
  }

  @Override
  public synchronized BlockBlobSidecarsTracker getOrCreateBlockBlobSidecarsTracker(
      final SignedBeaconBlock block) {
    return internalOnNewBlock(block, Optional.empty());
  }

  @Override
  public synchronized Optional<BlockBlobSidecarsTracker> getBlockBlobSidecarsTracker(
      final SignedBeaconBlock block) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(block.getRoot()));
  }

  @Override
  public synchronized void onCompletedBlockAndBlobSidecars(
      final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {
    if (recentChainData.containsBlock(block.getRoot())) {
      return;
    }
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blobSidecarsTracker =
        getOrCreateBlobSidecarsTracker(slotAndBlockRoot, __ -> {}, __ -> {});

    blobSidecarsTracker.setBlock(block);

    long addedBlobs =
        blobSidecars.stream()
            .map(
                blobSidecar -> {
                  final boolean isNew = blobSidecarsTracker.add(blobSidecar);
                  if (isNew) {
                    newBlobSidecarSubscribers.deliver(
                        NewBlobSidecarSubscriber::onNewBlobSidecar, blobSidecar);
                  }
                  return isNew;
                })
            .filter(Boolean::booleanValue)
            .count();
    totalBlobSidecars += (int) addedBlobs;
    sizeGauge.set(totalBlobSidecars, GAUGE_BLOB_SIDECARS_LABEL);

    if (!blobSidecarsTracker.isCompleted()) {
      LOG.error(
          "Tracker for block {} is supposed to be completed but it is not. Missing blob sidecars: {}",
          block.toLogString(),
          blobSidecarsTracker.getMissingBlobSidecars().count());
    }

    if (orderedBlobSidecarsTrackers.add(slotAndBlockRoot)) {
      sizeGauge.set(orderedBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  @Override
  public synchronized void removeAllForBlock(final Bytes32 blockRoot) {
    final BlockBlobSidecarsTracker removedTracker = blockBlobSidecarsTrackers.remove(blockRoot);

    if (removedTracker != null) {
      orderedBlobSidecarsTrackers.remove(removedTracker.getSlotAndBlockRoot());

      dropMissingContent(removedTracker);

      totalBlobSidecars -= removedTracker.blobSidecarsCount();
      sizeGauge.set(totalBlobSidecars, GAUGE_BLOB_SIDECARS_LABEL);
      sizeGauge.set(blockBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }
  }

  @VisibleForTesting
  BlockBlobSidecarsTracker getBlobSidecarsTracker(final SlotAndBlockRoot slotAndBlockRoot) {
    return blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    super.onSlot(slot);

    LOG.trace(
        "Trackers: {}",
        () -> {
          synchronized (this) {
            return blockBlobSidecarsTrackers.toString();
          }
        });
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

    toRemove.stream().map(SlotAndBlockRoot::getBlockRoot).forEach(this::removeAllForBlock);
  }

  @Override
  public synchronized boolean containsBlobSidecar(final BlobIdentifier blobIdentifier) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(blobIdentifier.getBlockRoot()))
        .map(tracker -> tracker.containsBlobSidecar(blobIdentifier))
        .orElse(false);
  }

  @Override
  public Optional<BlobSidecar> getBlobSidecar(final Bytes32 blockRoot, final UInt64 index) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(blockRoot))
        .flatMap(tracker -> tracker.getBlobSidecar(index));
  }

  @Override
  public synchronized boolean containsBlock(final Bytes32 blockRoot) {
    return getBlock(blockRoot).isPresent();
  }

  @Override
  public synchronized Optional<SignedBeaconBlock> getBlock(final Bytes32 blockRoot) {
    return Optional.ofNullable(blockBlobSidecarsTrackers.get(blockRoot))
        .flatMap(BlockBlobSidecarsTracker::getBlock);
  }

  @Override
  public synchronized Set<BlobIdentifier> getAllRequiredBlobSidecars() {
    return blockBlobSidecarsTrackers.values().stream()
        .flatMap(BlockBlobSidecarsTracker::getMissingBlobSidecars)
        .collect(Collectors.toSet());
  }

  @Override
  public synchronized void enableBlockImportOnCompletion(final SignedBeaconBlock block) {
    Optional.ofNullable(blockBlobSidecarsTrackers.get(block.getRoot()))
        .ifPresent(tracker -> tracker.enableBlockImportOnCompletion(blockImportChannel));
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

  @Override
  public void subscribeNewBlobSidecar(final NewBlobSidecarSubscriber newBlobSidecarSubscriber) {
    newBlobSidecarSubscribers.subscribe(newBlobSidecarSubscriber);
  }

  public synchronized int getTotalBlobSidecars() {
    return totalBlobSidecars;
  }

  public synchronized int getTotalBlobSidecarsTrackers() {
    return blockBlobSidecarsTrackers.size();
  }

  private BlockBlobSidecarsTracker internalOnNewBlock(
      final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker tracker =
        getOrCreateBlobSidecarsTracker(
            slotAndBlockRoot,
            newTracker -> {
              newTracker.setBlock(block);
              countBlock(remoteOrigin);
              onFirstSeen(slotAndBlockRoot);
            },
            existingTracker -> {
              if (!existingTracker.setBlock(block)) {
                // block was already set
                countDuplicateBlock(remoteOrigin);
                return;
              }

              countBlock(remoteOrigin);

              if (existingTracker.isRpcFetchTriggered()) {
                // block has been set for the first time and we previously triggered fetching of
                // missing blobSidecars. So we may have requested to fetch more sidecars
                // than the block actually requires. Let's drop them.
                existingTracker
                    .getUnusedBlobSidecarsForBlock()
                    .forEach(
                        blobIdentifier ->
                            requiredBlobSidecarDroppedSubscribers.deliver(
                                RequiredBlobSidecarDroppedSubscriber::onRequiredBlobSidecarDropped,
                                blobIdentifier));

                // if we attempted to fetch via RPC, we missed the opportunity to complete the
                // tracker via local EL (local El fetch requires the block to be known)
                // Let's try now
                if (!existingTracker.isLocalElFetchTriggered() && !existingTracker.isCompleted()) {
                  fetchMissingContentFromLocalEL(slotAndBlockRoot)
                      .finish(
                          error ->
                              LOG.error(
                                  "An occurred while attempting to fetch blobs via local EL"));
                }
              }
            });

    if (orderedBlobSidecarsTrackers.add(slotAndBlockRoot)) {
      sizeGauge.set(orderedBlobSidecarsTrackers.size(), GAUGE_BLOB_SIDECARS_TRACKERS_LABEL);
    }

    return tracker;
  }

  private void countBlock(final Optional<RemoteOrigin> maybeRemoteOrigin) {
    maybeRemoteOrigin.ifPresent(
        remoteOrigin -> {
          switch (remoteOrigin) {
            case RPC -> poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_RPC_SUBTYPE).inc();
            case GOSSIP ->
                poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_GOSSIP_SUBTYPE).inc();
            case LOCAL_EL -> {} // only possible for blobs
          }
        });
  }

  private void countDuplicateBlock(final Optional<RemoteOrigin> maybeRemoteOrigin) {
    maybeRemoteOrigin.ifPresent(
        remoteOrigin -> {
          switch (remoteOrigin) {
            case RPC ->
                poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_RPC_DUPLICATE_SUBTYPE).inc();
            case GOSSIP ->
                poolStatsCounters
                    .labels(COUNTER_BLOCK_TYPE, COUNTER_GOSSIP_DUPLICATE_SUBTYPE)
                    .inc();
            case LOCAL_EL -> {} // only possible for blobs
          }
        });
  }

  private BlockBlobSidecarsTracker getOrCreateBlobSidecarsTracker(
      final SlotAndBlockRoot slotAndBlockRoot,
      final Consumer<BlockBlobSidecarsTracker> onNew,
      final Consumer<BlockBlobSidecarsTracker> onExisting) {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());
    if (blockBlobSidecarsTracker == null) {
      makeRoomForNewTracker();
      blockBlobSidecarsTracker = trackerFactory.create(slotAndBlockRoot);
      blockBlobSidecarsTrackers.put(slotAndBlockRoot.getBlockRoot(), blockBlobSidecarsTracker);
      onNew.accept(blockBlobSidecarsTracker);
    } else {
      onExisting.accept(blockBlobSidecarsTracker);
    }
    return blockBlobSidecarsTracker;
  }

  private void makeRoomForNewTracker() {
    while (blockBlobSidecarsTrackers.size() > (maxTrackers - 1)) {
      final SlotAndBlockRoot toRemove = orderedBlobSidecarsTrackers.pollFirst();
      if (toRemove == null) {
        break;
      }
      removeAllForBlock(toRemove.getBlockRoot());
    }
  }

  private void onFirstSeen(final SlotAndBlockRoot slotAndBlockRoot) {
    final Duration fetchDelay = calculateFetchDelay(slotAndBlockRoot);

    asyncRunner
        .runAfterDelay(
            () ->
                this.fetchMissingContentFromLocalEL(slotAndBlockRoot)
                    .handleException(
                        error ->
                            LOG.warn(
                                "Local EL blobs lookup failed: {}",
                                ExceptionUtils.getMessage(error)))
                    .thenRun(() -> this.fetchMissingContentFromRemotePeers(slotAndBlockRoot)),
            fetchDelay)
        .finish(
            error ->
                LOG.error("An error occurred while attempting to fetch missing blobs.", error));
  }

  @VisibleForTesting
  Duration calculateFetchDelay(final SlotAndBlockRoot slotAndBlockRoot) {
    final UInt64 slot = slotAndBlockRoot.getSlot();

    if (slot.isLessThan(getCurrentSlot())) {
      // old slot
      return Duration.ZERO;
    }

    final UInt64 nowMillis = timeProvider.getTimeInMillis();
    final UInt64 slotStartTimeMillis = secondsToMillis(recentChainData.computeTimeAtSlot(slot));
    final UInt64 millisPerSlot = secondsToMillis(spec.getSecondsPerSlot(slot));
    final UInt64 attestationDueMillis = slotStartTimeMillis.plus(millisPerSlot.dividedBy(3));

    if (nowMillis.isGreaterThanOrEqualTo(attestationDueMillis)) {
      // late block, we already produced attestations on previous head,
      // so let's wait our target delay before trying to fetch
      return Duration.ofMillis(TARGET_WAIT_MILLIS.intValue());
    }

    final UInt64 upperLimitRelativeToAttDue =
        attestationDueMillis.minus(MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS);

    final UInt64 targetMillis = nowMillis.plus(TARGET_WAIT_MILLIS);

    final UInt64 finalTime =
        targetMillis.min(upperLimitRelativeToAttDue).max(nowMillis.plus(MIN_WAIT_MILLIS));

    return Duration.ofMillis(finalTime.minus(nowMillis).intValue());
  }

  private synchronized SafeFuture<Void> fetchMissingContentFromLocalEL(
      final SlotAndBlockRoot slotAndBlockRoot) {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());

    if (blockBlobSidecarsTracker == null) {
      return SafeFuture.COMPLETE;
    }

    if (blockBlobSidecarsTracker.isCompleted()) {
      return SafeFuture.COMPLETE;
    }

    if (blockBlobSidecarsTracker.getBlock().isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    blockBlobSidecarsTracker.setLocalElFetchTriggered();

    final SpecVersion specVersion = spec.atSlot(slotAndBlockRoot.getSlot());
    final BlobSidecarSchema blobSidecarSchema =
        specVersion.getSchemaDefinitions().toVersionDeneb().orElseThrow().getBlobSidecarSchema();
    final MiscHelpersDeneb miscHelpersDeneb =
        specVersion.miscHelpers().toVersionDeneb().orElseThrow();
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        blockBlobSidecarsTracker.getBlock().get().asHeader();
    final BeaconBlockBodyDeneb beaconBlockBodyDeneb =
        blockBlobSidecarsTracker
            .getBlock()
            .get()
            .getMessage()
            .getBody()
            .toVersionDeneb()
            .orElseThrow();

    final SszList<SszKZGCommitment> sszKZGCommitments =
        beaconBlockBodyDeneb.getBlobKzgCommitments();

    final List<BlobIdentifier> missingBlobsIdentifiers =
        blockBlobSidecarsTracker.getMissingBlobSidecars().toList();

    final List<VersionedHash> versionedHashes =
        missingBlobsIdentifiers.stream()
            .map(
                blobIdentifier ->
                    miscHelpersDeneb.kzgCommitmentToVersionedHash(
                        sszKZGCommitments
                            .get(blobIdentifier.getIndex().intValue())
                            .getKZGCommitment()))
            .toList();

    poolStatsCounters
        .labels(COUNTER_SIDECAR_TYPE, COUNTER_LOCAL_EL_FETCH_SUBTYPE)
        .inc(versionedHashes.size());

    return executionLayer
        .engineGetBlobs(versionedHashes, slotAndBlockRoot.getSlot())
        .thenAccept(
            blobAndProofs -> {
              checkArgument(
                  blobAndProofs.size() == versionedHashes.size(),
                  "Queried %s versionedHashed but got %s blobAndProofs",
                  versionedHashes.size(),
                  blobAndProofs.size());

              for (int index = 0; index < blobAndProofs.size(); index++) {
                final Optional<BlobAndProof> blobAndProof = blobAndProofs.get(index);
                if (blobAndProof.isEmpty()) {
                  LOG.trace("Blob not found on local EL: {}", missingBlobsIdentifiers.get(index));
                  continue;
                }

                final BlobSidecar blobSidecar =
                    createBlobSidecarFromBlobAndProof(
                        blobSidecarSchema,
                        miscHelpersDeneb,
                        missingBlobsIdentifiers.get(index),
                        blobAndProof.get(),
                        beaconBlockBodyDeneb,
                        signedBeaconBlockHeader);

                onNewBlobSidecar(blobSidecar, LOCAL_EL);
              }
            });
  }

  private BlobSidecar createBlobSidecarFromBlobAndProof(
      final BlobSidecarSchema blobSidecarSchema,
      final MiscHelpersDeneb miscHelpersDeneb,
      final BlobIdentifier blobIdentifier,
      final BlobAndProof blobAndProof,
      final BeaconBlockBodyDeneb beaconBlockBodyDeneb,
      final SignedBeaconBlockHeader signedBeaconBlockHeader) {

    final SszKZGCommitment sszKZGCommitment =
        beaconBlockBodyDeneb.getBlobKzgCommitments().get(blobIdentifier.getIndex().intValue());

    return blobSidecarSchema.create(
        blobIdentifier.getIndex(),
        blobAndProof.blob(),
        sszKZGCommitment,
        new SszKZGProof(blobAndProof.proof()),
        signedBeaconBlockHeader,
        miscHelpersDeneb.computeKzgCommitmentInclusionProof(
            blobIdentifier.getIndex(), beaconBlockBodyDeneb));
  }

  private synchronized void fetchMissingContentFromRemotePeers(
      final SlotAndBlockRoot slotAndBlockRoot) {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        blockBlobSidecarsTrackers.get(slotAndBlockRoot.getBlockRoot());

    if (blockBlobSidecarsTracker == null) {
      return;
    }

    if (blockBlobSidecarsTracker.isCompleted()) {
      return;
    }

    blockBlobSidecarsTracker.setRpcFetchTriggered();

    if (blockBlobSidecarsTracker.getBlock().isEmpty()) {
      poolStatsCounters.labels(COUNTER_BLOCK_TYPE, COUNTER_RPC_FETCH_SUBTYPE).inc();
      requiredBlockRootSubscribers.deliver(
          RequiredBlockRootSubscriber::onRequiredBlockRoot,
          blockBlobSidecarsTracker.getSlotAndBlockRoot().getBlockRoot());
    }

    blockBlobSidecarsTracker
        .getMissingBlobSidecars()
        .forEach(
            blobIdentifier -> {
              poolStatsCounters.labels(COUNTER_SIDECAR_TYPE, COUNTER_RPC_FETCH_SUBTYPE).inc();
              requiredBlobSidecarSubscribers.deliver(
                  RequiredBlobSidecarSubscriber::onRequiredBlobSidecar, blobIdentifier);
            });
  }

  private void dropMissingContent(final BlockBlobSidecarsTracker blockBlobSidecarsTracker) {

    if (!blockBlobSidecarsTracker.isRpcFetchTriggered()) {
      return;
    }

    if (blockBlobSidecarsTracker.getBlock().isEmpty()) {
      requiredBlockRootDroppedSubscribers.deliver(
          RequiredBlockRootDroppedSubscriber::onRequiredBlockRootDropped,
          blockBlobSidecarsTracker.getSlotAndBlockRoot().getBlockRoot());
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
