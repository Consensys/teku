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
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.sync.events.SyncStateProvider;

public class HistoricalBlockSyncService extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private static final Duration RETRY_TIMEOUT = Duration.ofMinutes(1);
  private static final UInt64 BATCH_SIZE = UInt64.valueOf(50);

  private final StorageUpdateChannel storageUpdateChannel;
  private final AsyncRunner asyncRunner;
  private final P2PNetwork<Eth2Peer> network;
  private final CombinedChainDataClient chainData;
  private final SyncStateProvider syncStateProvider;

  private final AtomicLong syncStateSubscription = new AtomicLong(-1);
  private final AtomicBoolean requestInProgress = new AtomicBoolean(false);

  private volatile BeaconBlockSummary earliestBlock;
  final Set<Eth2Peer> badPeerCache;

  public HistoricalBlockSyncService(
      final StorageUpdateChannel storageUpdateChannel,
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> network,
      final CombinedChainDataClient chainData,
      final SyncStateProvider syncStateProvider) {
    this.storageUpdateChannel = storageUpdateChannel;

    this.asyncRunner = asyncRunner;
    this.network = network;
    this.chainData = chainData;
    this.syncStateProvider = syncStateProvider;

    this.badPeerCache =
        Collections.newSetFromMap(
            CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(5))
                .<Eth2Peer, Boolean>build()
                .asMap());
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.debug("Start {}", getClass().getSimpleName());
    return initialize().thenAccept(__ -> requestBlocksIfAppropriate());
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.debug("Stop {}", getClass().getSimpleName());
    syncStateProvider.unsubscribeFromSyncStateChanges(syncStateSubscription.get());
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
              }
              syncStateSubscription.set(
                  syncStateProvider.subscribeToSyncStateChanges(
                      __ -> requestBlocksIfAppropriate()));
            });
  }

  private void requestBlocksIfAppropriate() {
    if (earliestBlock.getSlot().equals(UInt64.ZERO)) {
      // Nothing to do - we're caught up to genesis
      LOG.info("Historical block sync is complete");
      this.stop().reportExceptions();
    } else if (isRunning() && syncStateProvider.getCurrentSyncState().isInSync()) {
      // Pull the next batch of blocks
      findPeerAndRequestBlocks();
    }
  }

  private void findPeerAndRequestBlocks() {
    if (requestInProgress.compareAndSet(false, true)) {
      findPeer()
          .map(this::requestBlocks)
          .orElseGet(this::waitToRetry)
          .alwaysRun(() -> requestInProgress.set(false))
          .always(this::requestBlocksIfAppropriate);
    }
  }

  private SafeFuture<Void> requestBlocks(final Eth2Peer peer) {
    return createFetcher(peer)
        .run()
        .exceptionally(
            (err) -> {
              // We ran into trouble with this peer - ignore it for a while
              LOG.debug(
                  "Encountered a problem requesting historical blocks from peer: " + peer, err);
              badPeerCache.add(peer);
              return null;
            })
        .thenAccept(
            newValue -> {
              if (newValue != null && newValue.getSlot().isLessThan(earliestBlock.getSlot())) {
                LOG.trace("Synced historical blocks to slot {}", newValue.getSlot());
                earliestBlock = newValue;
              }
            });
  }

  private HistoricalBatchFetcher createFetcher(final Eth2Peer peer) {
    final UInt64 maxSlot;
    final Bytes32 lastBlockRoot;
    if (earliestBlock.getBeaconBlock().isPresent()) {
      maxSlot = earliestBlock.getSlot().minus(1);
      lastBlockRoot = earliestBlock.getParentRoot();
    } else {
      maxSlot = earliestBlock.getSlot();
      lastBlockRoot = earliestBlock.getRoot();
    }

    return HistoricalBatchFetcher.create(
        storageUpdateChannel, peer, maxSlot, lastBlockRoot, BATCH_SIZE);
  }

  private SafeFuture<Void> waitToRetry() {
    return asyncRunner.getDelayedFuture(RETRY_TIMEOUT);
  }

  private Optional<Eth2Peer> findPeer() {
    return network
        .streamPeers()
        .filter(p -> !badPeerCache.contains(p))
        .filter(
            p ->
                p.getStatus()
                    .getFinalizedCheckpoint()
                    .getEpochStartSlot()
                    .isGreaterThan(earliestBlock.getSlot()))
        .findAny();
  }
}
