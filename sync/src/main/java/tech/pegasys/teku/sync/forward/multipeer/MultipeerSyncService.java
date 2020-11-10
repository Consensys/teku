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

package tech.pegasys.teku.sync.forward.multipeer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.OrderedAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.events.SyncingStatus;
import tech.pegasys.teku.sync.forward.ForwardSyncService;
import tech.pegasys.teku.sync.forward.multipeer.batches.BatchFactory;
import tech.pegasys.teku.sync.forward.multipeer.batches.PeerScoringConflictResolutionStrategy;
import tech.pegasys.teku.sync.forward.multipeer.chains.PeerChainTracker;
import tech.pegasys.teku.sync.forward.multipeer.chains.SyncSourceFactory;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChains;
import tech.pegasys.teku.util.config.Constants;

public class MultipeerSyncService extends Service implements ForwardSyncService {
  private static final Logger LOG = LogManager.getLogger();
  private final EventThread eventThread;
  private final RecentChainData recentChainData;
  private final PeerChainTracker peerChainTracker;
  private final SyncController syncController;

  MultipeerSyncService(
      final EventThread eventThread,
      final RecentChainData recentChainData,
      final PeerChainTracker peerChainTracker,
      final SyncController syncController) {
    this.eventThread = eventThread;
    this.recentChainData = recentChainData;
    this.peerChainTracker = peerChainTracker;
    this.syncController = syncController;
  }

  public static MultipeerSyncService create(
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final P2PNetwork<Eth2Peer> p2pNetwork,
      final BlockImporter blockImporter) {
    LOG.info("Using multipeer sync");
    final EventThread eventThread = new AsyncRunnerEventThread("sync", asyncRunnerFactory);

    final TargetChains finalizedTargetChains = new TargetChains();
    final TargetChains nonfinalizedTargetChains = new TargetChains();
    final BatchSync batchSync =
        BatchSync.create(
            eventThread,
            recentChainData,
            new BatchImporter(blockImporter, asyncRunner),
            new BatchFactory(eventThread, new PeerScoringConflictResolutionStrategy()),
            Constants.SYNC_BATCH_SIZE,
            MultipeerCommonAncestorFinder.create(recentChainData, eventThread));
    final SyncController syncController =
        new SyncController(
            eventThread,
            new OrderedAsyncRunner(asyncRunner),
            recentChainData,
            new ChainSelector(recentChainData, finalizedTargetChains),
            new ChainSelector(recentChainData, nonfinalizedTargetChains),
            batchSync);
    final PeerChainTracker peerChainTracker =
        new PeerChainTracker(
            eventThread,
            p2pNetwork,
            new SyncSourceFactory(asyncRunner, timeProvider),
            finalizedTargetChains,
            nonfinalizedTargetChains);
    peerChainTracker.subscribeToTargetChainUpdates(syncController::onTargetChainsUpdated);
    return new MultipeerSyncService(eventThread, recentChainData, peerChainTracker, syncController);
  }

  @Override
  protected SafeFuture<?> doStart() {
    // We shouldn't start syncing until we have reached genesis.
    // There are also no valid blocks until we've reached genesis so no point in gossipping and
    // queuing them
    recentChainData.subscribeStoreInitialized(
        () -> {
          eventThread.start();
          peerChainTracker.start();
        });
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    peerChainTracker.stop();
    eventThread.stop();
    return SafeFuture.COMPLETE;
  }

  @Override
  public SyncingStatus getSyncStatus() {
    return syncController.getSyncStatus();
  }

  @Override
  public boolean isSyncActive() {
    return syncController.isSyncActive();
  }

  @Override
  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return syncController.subscribeToSyncChanges(subscriber);
  }

  @Override
  public void unsubscribeFromSyncChanges(final long subscriberId) {
    syncController.unsubscribeFromSyncChanges(subscriberId);
  }
}
