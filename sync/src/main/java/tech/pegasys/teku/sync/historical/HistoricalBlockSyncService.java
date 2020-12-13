/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.sync.historical;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.sync.events.SyncStateProvider;

/**
 * Service responsible for syncing missing historical blocks. Blocks are pulled in order from the
 * newest unknown block back to genesis.
 */
public class HistoricalBlockSyncService extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private static final Duration RETRY_TIMEOUT = Duration.ofMinutes(1);
  private static final UInt64 BATCH_SIZE = UInt64.valueOf(50);

  private final SettableGauge historicSyncGauge;
  private final StorageUpdateChannel storageUpdateChannel;
  private final AsyncRunner asyncRunner;
  private final P2PNetwork<Eth2Peer> network;
  private final CombinedChainDataClient chainData;
  private final SyncStateProvider syncStateProvider;
  private final UInt64 batchSize;

  private final AtomicLong syncStateSubscription = new AtomicLong(-1);
  private final AtomicBoolean requestInProgress = new AtomicBoolean(false);

  private volatile BeaconBlockSummary earliestBlock;
  final Set<NodeId> badPeerCache;

  @VisibleForTesting
  HistoricalBlockSyncService(
      final MetricsSystem metricsSystem,
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> network,
      final CombinedChainDataClient chainData,
      final SyncStateProvider syncStateProvider,
      final UInt64 batchSize) {
    this.storageUpdateChannel = storageUpdateChannel;

    this.asyncRunner = asyncRunner;
    this.network = network;
    this.chainData = chainData;
    this.syncStateProvider = syncStateProvider;
    this.batchSize = batchSize;

    this.badPeerCache =
        Collections.newSetFromMap(
            CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(5))
                .removalListener(__ -> logBadPeerCacheSize(false))
                .<NodeId, Boolean>build()
                .asMap());

    this.historicSyncGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "historical_block_sync_earliest_block",
            "The slot of the earliest block retrieved by the historical block sync service");
  }

  public static HistoricalBlockSyncService create(
      final MetricsSystem metricsSystem,
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> network,
      final CombinedChainDataClient chainData,
      final SyncStateProvider syncStateProvider) {
    return new HistoricalBlockSyncService(
        metricsSystem,
        storageUpdateChannel,
        asyncRunner,
        network,
        chainData,
        syncStateProvider,
        BATCH_SIZE);
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.debug("Start {}", getClass().getSimpleName());
    return initialize().thenRun(this::fetchBlocks);
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.debug("Stop {}", getClass().getSimpleName());
    syncStateProvider.unsubscribeFromSyncStateChanges(syncStateSubscription.get());
    badPeerCache.clear();
    return SafeFuture.COMPLETE;
  }

  private SafeFuture<Void> initialize() {
    return chainData
        .getEarliestAvailableBlockSummary()
        .thenAccept(
            beaconBlockSummary -> {
              this.earliestBlock =
                  beaconBlockSummary.orElseThrow(
                      () -> new IllegalStateException("Unable to retrieve earliest block"));
              if (earliestBlock.getSlot().isGreaterThan(UInt64.ZERO)) {
                LOG.info(
                    "Begin historical sync of blocks prior to slot {}", earliestBlock.getSlot());
                updateSyncMetrics();
              }
              syncStateSubscription.set(
                  syncStateProvider.subscribeToSyncStateChanges(__ -> fetchBlocks()));
            });
  }

  private void updateSyncMetrics() {
    if (earliestBlock.getBeaconBlock().isPresent()) {
      historicSyncGauge.set(earliestBlock.getSlot().doubleValue());
    }
  }

  private void fetchBlocks() {
    SafeFuture.asyncDoWhile(this::findPeerAndRequestBlocks)
        .always(
            () -> {
              if (isSyncDone()) {
                stop().reportExceptions();
              }
            });
  }

  private SafeFuture<Boolean> findPeerAndRequestBlocks() {
    final Optional<MaxMissingBlockParams> blockParams = getMaxMissingBlockParams();
    if (blockParams.isPresent() && isActive() && requestInProgress.compareAndSet(false, true)) {
      return findPeer()
          .map(peer -> requestBlocks(peer, blockParams.get()))
          .orElseGet(this::waitToRetry)
          .alwaysRun(() -> requestInProgress.set(false))
          .thenApply(__ -> true);
    } else {
      return SafeFuture.completedFuture(false);
    }
  }

  private boolean isActive() {
    return isRunning() && syncStateProvider.getCurrentSyncState().isInSync();
  }

  private SafeFuture<Void> requestBlocks(final Eth2Peer peer, final MaxMissingBlockParams params) {
    return createFetcher(peer, params)
        .run()
        .exceptionally(
            (err) -> {
              // We ran into trouble with this peer - ignore it for a while
              LOG.debug(
                  "Encountered a problem requesting historical blocks from peer: " + peer, err);
              if (peer.isConnected()) {
                // If we didn't disconnect the peer altogether, avoid making new requests for a
                // while
                badPeerCache.add(peer.getId());
                logBadPeerCacheSize(true);
              }
              return null;
            })
        .thenAccept(
            newValue -> {
              if (newValue != null && newValue.getSlot().isLessThanOrEqualTo(params.getMaxSlot())) {
                LOG.trace("Synced historical blocks to slot {}", newValue.getSlot());
                earliestBlock = newValue;
                updateSyncMetrics();
                if (isSyncDone()) {
                  LOG.info("Historical block sync is complete");
                }
              }
            });
  }

  private HistoricalBatchFetcher createFetcher(
      final Eth2Peer peer, final MaxMissingBlockParams params) {
    return HistoricalBatchFetcher.create(
        storageUpdateChannel, peer, params.getMaxSlot(), params.getBlockRoot(), batchSize);
  }

  private boolean isSyncDone() {
    return earliestBlock.getBeaconBlock().map(b -> b.getSlot().equals(UInt64.ZERO)).orElse(false);
  }

  private Optional<MaxMissingBlockParams> getMaxMissingBlockParams() {
    final UInt64 maxSlot;
    final Bytes32 lastBlockRoot;
    if (earliestBlock.getBeaconBlock().isPresent()) {
      if (earliestBlock.getSlot().equals(UInt64.ZERO)) {
        // Nothing left to request
        return Optional.empty();
      }
      maxSlot = earliestBlock.getSlot().minus(1);
      lastBlockRoot = earliestBlock.getParentRoot();
    } else {
      maxSlot = earliestBlock.getSlot();
      lastBlockRoot = earliestBlock.getRoot();
    }

    return Optional.of(new MaxMissingBlockParams(lastBlockRoot, maxSlot));
  }

  private SafeFuture<Void> waitToRetry() {
    return asyncRunner.getDelayedFuture(RETRY_TIMEOUT);
  }

  private Optional<Eth2Peer> findPeer() {
    return network
        .streamPeers()
        .filter(p -> !badPeerCache.contains(p.getId()))
        .filter(
            p ->
                p.getStatus()
                    .getFinalizedCheckpoint()
                    .getEpochStartSlot()
                    .isGreaterThan(earliestBlock.getSlot()))
        .findAny();
  }

  private void logBadPeerCacheSize(final boolean peerAdded) {
    if (peerAdded) {
      LOG.trace("Peer added to bad peer cache, current size: {}", badPeerCache.size());
    } else {
      LOG.trace("Peer removed from bad peer cache, current size: {}", badPeerCache.size());
    }
  }

  private static class MaxMissingBlockParams {
    private final Bytes32 blockRoot;
    private final UInt64 maxSlot;

    private MaxMissingBlockParams(final Bytes32 blockRoot, final UInt64 maxSlot) {
      this.maxSlot = maxSlot;
      this.blockRoot = blockRoot;
    }

    public Bytes32 getBlockRoot() {
      return blockRoot;
    }

    public UInt64 getMaxSlot() {
      return maxSlot;
    }
  }
}
