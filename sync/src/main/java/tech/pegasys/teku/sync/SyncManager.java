/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.sync;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService.SyncSubscriber;
import tech.pegasys.teku.util.events.Subscribers;

public class SyncManager extends Service {
  private static final Duration SHORT_DELAY = Duration.ofSeconds(5);
  private static final Duration LONG_DELAY = Duration.ofSeconds(20);
  private static final UInt64 SYNC_THRESHOLD_IN_EPOCHS = UInt64.ONE;
  private static final UInt64 SYNC_THRESHOLD_IN_SLOTS =
      SYNC_THRESHOLD_IN_EPOCHS.times(SLOTS_PER_EPOCH);

  private static final Logger LOG = LogManager.getLogger();
  private final P2PNetwork<Eth2Peer> network;
  private final RecentChainData storageClient;
  private final PeerSync peerSync;
  private final Subscribers<SyncSubscriber> syncSubscribers = Subscribers.create(true);

  private boolean syncActive = false;
  private boolean syncQueued = false;
  /**
   * Tracks the last state we notified subscribers of. It differs from syncActive at the start of a
   * sync because we set syncActive as soon as we begin, but only notify subscribers once we've
   * actually found a valid peer to sync off to avoid briefly toggling to syncing and back off each
   * time we look for sync targets.
   */
  private boolean subscribersSyncActive = false;

  private volatile long peerConnectSubscriptionId;

  private final AsyncRunner asyncRunner;
  private final Set<NodeId> peersWithSyncErrors = new HashSet<>();

  SyncManager(
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> network,
      final RecentChainData storageClient,
      final PeerSync peerSync) {
    this.asyncRunner = asyncRunner;
    this.network = network;
    this.storageClient = storageClient;
    this.peerSync = peerSync;
  }

  public static SyncManager create(
      final AsyncRunner asyncRunner,
      final P2PNetwork<Eth2Peer> network,
      final RecentChainData storageClient,
      final BlockImporter blockImporter,
      final MetricsSystem metricsSystem) {
    return new SyncManager(
        asyncRunner,
        network,
        storageClient,
        new PeerSync(asyncRunner, storageClient, blockImporter, metricsSystem));
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.trace("Start {}", this.getClass().getSimpleName());
    peerConnectSubscriptionId = network.subscribeConnect(this::onNewPeer);
    startOrScheduleSync();
    return completedFuture(null);
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.trace("Stop {}", this.getClass().getSimpleName());
    network.unsubscribeConnect(peerConnectSubscriptionId);
    synchronized (this) {
      syncQueued = false;
    }
    peerSync.stop();
    return completedFuture(null);
  }

  private void startOrScheduleSync() {
    synchronized (this) {
      if (syncActive) {
        if (!syncQueued) {
          LOG.trace("Queue sync");
          syncQueued = true;
        }
        return;
      }
      syncActive = true;
    }

    startSync();
  }

  private void startSync() {
    executeSync()
        .finish(
            () -> {
              synchronized (SyncManager.this) {
                if (syncQueued) {
                  syncQueued = false;
                  startSync();
                } else {
                  syncActive = false;
                  if (subscribersSyncActive) {
                    subscribersSyncActive = false;
                    syncSubscribers.deliver(SyncSubscriber::onSyncingChange, false);
                  }
                }
              }
            },
            error -> LOG.error("Unexpected error during sync", error));
  }

  @VisibleForTesting
  synchronized boolean isSyncActive() {
    return syncActive;
  }

  @VisibleForTesting
  synchronized boolean isSyncQueued() {
    return syncQueued;
  }

  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return syncSubscribers.subscribe(subscriber);
  }

  public void unsubscribeFromSyncChanges(final long subscriberId) {
    syncSubscribers.unsubscribe(subscriberId);
  }

  public SyncingStatus getSyncStatus() {
    final boolean isSyncActive = isSyncActive();
    if (isSyncActive) {
      Optional<Eth2Peer> bestPeer = findBestSyncPeer();
      if (bestPeer.isPresent()) {
        UInt64 highestSlot = bestPeer.get().getStatus().getHeadSlot();
        final SyncStatus syncStatus =
            new SyncStatus(peerSync.getStartingSlot(), storageClient.getBestSlot(), highestSlot);
        return new SyncingStatus(true, syncStatus);
      }
    }
    return new SyncingStatus(false, null);
  }

  private SafeFuture<Void> executeSync() {
    return findBestSyncPeer()
        .map(this::syncToPeer)
        .orElseGet(
            () -> {
              LOG.trace("No suitable peers (out of {}) found for sync.", network.getPeerCount());
              asyncRunner
                  .getDelayedFuture(LONG_DELAY.toMillis(), TimeUnit.MILLISECONDS)
                  .thenAccept((res) -> startOrScheduleSync())
                  .reportExceptions();
              return completedFuture(null);
            });
  }

  private SafeFuture<Void> syncToPeer(final Eth2Peer syncPeer) {
    LOG.trace("Sync to peer {}", syncPeer.getId());
    synchronized (this) {
      if (!subscribersSyncActive) {
        subscribersSyncActive = true;
        syncSubscribers.deliver(SyncSubscriber::onSyncingChange, true);
      }
    }
    return peerSync
        .sync(syncPeer)
        .thenCompose(
            result -> {
              if (result != PeerSyncResult.SUCCESSFUL_SYNC) {
                LOG.trace("Sync to peer {} failed with {}.", syncPeer.getId(), result.name());
                return asyncRunner.runAfterDelay(
                    this::executeSync, SHORT_DELAY.toMillis(), TimeUnit.MILLISECONDS);
              } else {
                LOG.trace("Successfully synced to peer {}.", syncPeer.getId());
                return completedFuture(null);
              }
            })
        .exceptionally(
            error -> {
              if (Throwables.getRootCause(error) instanceof PeerDisconnectedException) {
                LOG.debug("Peer {} disconnected during sync", syncPeer, error);

              } else {
                LOG.error("Error during sync to peer {}", syncPeer, error);
              }
              peersWithSyncErrors.add(syncPeer.getId());
              // Wait a little bit, clear error and retry
              asyncRunner
                  .getDelayedFuture(LONG_DELAY.toMillis(), TimeUnit.MILLISECONDS)
                  .thenAccept(
                      (res) -> {
                        peersWithSyncErrors.remove(syncPeer.getId());
                        startOrScheduleSync();
                      })
                  .reportExceptions();
              return null;
            });
  }

  Optional<Eth2Peer> findBestSyncPeer() {
    return network
        .streamPeers()
        .filter(this::isPeerSyncSuitable)
        .max(
            Comparator.comparing(Eth2Peer::finalizedEpoch)
                .thenComparing(peer -> peer.getStatus().getHeadSlot())
                .thenComparing(p -> Math.random()));
  }

  private void onNewPeer(Eth2Peer peer) {
    if (isPeerSyncSuitable(peer)) {
      LOG.trace("New peer connected ({}), schedule sync.", peer.getId());
      startOrScheduleSync();
    }
  }

  private boolean isPeerSyncSuitable(Eth2Peer peer) {
    UInt64 ourFinalizedEpoch = storageClient.getFinalizedEpoch();
    LOG.trace(
        "Looking for suitable peer (out of {}) with finalized epoch > {}.",
        network.getPeerCount(),
        ourFinalizedEpoch);

    final PeerStatus peerStatus = peer.getStatus();
    return !peersWithSyncErrors.contains(peer.getId())
        && peerStatusIsConsistentWithOurNode(peerStatus)
        && peerIsAheadOfOurNode(peerStatus, ourFinalizedEpoch);
  }

  /** Make sure remote peer is not broadcasting a chain state from the future. */
  private boolean peerStatusIsConsistentWithOurNode(final PeerStatus peerStatus) {
    final UInt64 currentSlot = storageClient.getCurrentSlot().orElse(UInt64.ZERO);
    final UInt64 currentEpoch = compute_epoch_at_slot(currentSlot);
    final UInt64 slotErrorThreshold = UInt64.ONE;

    return peerStatus.getFinalizedEpoch().isLessThanOrEqualTo(currentEpoch)
        && peerStatus.getHeadSlot().isLessThanOrEqualTo(currentSlot.plus(slotErrorThreshold));
  }

  private boolean peerIsAheadOfOurNode(
      final PeerStatus peerStatus, final UInt64 ourFinalizedEpoch) {
    final UInt64 finalizedEpochThreshold = ourFinalizedEpoch.plus(SYNC_THRESHOLD_IN_EPOCHS);

    return peerStatus.getFinalizedEpoch().isGreaterThan(finalizedEpochThreshold)
        || isPeerHeadSlotAhead(peerStatus);
  }

  private boolean isPeerHeadSlotAhead(final PeerStatus peerStatus) {
    final UInt64 ourHeadSlot = storageClient.getBestSlot();
    final UInt64 headSlotThreshold = ourHeadSlot.plus(SYNC_THRESHOLD_IN_SLOTS);

    return peerStatus.getHeadSlot().isGreaterThan(headSlotThreshold);
  }
}
