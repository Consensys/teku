/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchFactory;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.PeerScoringConflictResolutionStrategy;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.PeerChainTracker;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.SyncSourceFactory;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChains;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.OrderedAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;

public class MultipeerSyncService extends Service implements ForwardSyncService {
  private static final Logger LOG = LogManager.getLogger();
  private final SyncStallDetector syncStallDetector;
  private final EventThread eventThread;
  private final RecentChainData recentChainData;
  private final PeerChainTracker peerChainTracker;
  private final SyncController syncController;

  MultipeerSyncService(
      final EventThread eventThread,
      final RecentChainData recentChainData,
      final PeerChainTracker peerChainTracker,
      final SyncController syncController,
      final SyncStallDetector syncStallDetector) {
    this.eventThread = eventThread;
    this.recentChainData = recentChainData;
    this.peerChainTracker = peerChainTracker;
    this.syncController = syncController;
    this.syncStallDetector = syncStallDetector;
  }

  public static MultipeerSyncService create(
      final MetricsSystem metricsSystem,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final RecentChainData recentChainData,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final P2PNetwork<Eth2Peer> p2pNetwork,
      final BlockImporter blockImporter,
      final Spec spec) {
    final EventThread eventThread = new AsyncRunnerEventThread("sync", asyncRunnerFactory);
    final SettableLabelledGauge targetChainCountGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.LIBP2P,
            "target_chains_current",
            "Current number of target chains being tracked",
            "type");
    final TargetChains finalizedTargetChains = new TargetChains(targetChainCountGauge, "finalized");
    final TargetChains nonfinalizedTargetChains =
        new TargetChains(targetChainCountGauge, "nonfinalized");
    final BatchSync batchSync =
        BatchSync.create(
            eventThread,
            asyncRunner,
            recentChainData,
            new BatchImporter(blockImporter, asyncRunner),
            new BatchFactory(eventThread, new PeerScoringConflictResolutionStrategy()),
            Constants.SYNC_BATCH_SIZE,
            MultipeerCommonAncestorFinder.create(recentChainData, eventThread, spec),
            timeProvider);
    final SyncController syncController =
        new SyncController(
            eventThread,
            new OrderedAsyncRunner(asyncRunner),
            recentChainData,
            new SyncTargetSelector(
                recentChainData,
                pendingBlocks,
                finalizedTargetChains,
                nonfinalizedTargetChains,
                spec.getSlotsPerEpoch(recentChainData.getCurrentSlot().orElse(UInt64.ZERO))),
            batchSync);
    final PeerChainTracker peerChainTracker =
        new PeerChainTracker(
            recentChainData.getSpec(),
            eventThread,
            p2pNetwork,
            new SyncSourceFactory(asyncRunner, timeProvider),
            finalizedTargetChains,
            nonfinalizedTargetChains);
    peerChainTracker.subscribeToTargetChainUpdates(syncController::onTargetChainsUpdated);
    final SyncStallDetector syncStallDetector =
        new SyncStallDetector(
            spec,
            eventThread,
            asyncRunner,
            timeProvider,
            syncController,
            batchSync,
            recentChainData);
    return new MultipeerSyncService(
        eventThread, recentChainData, peerChainTracker, syncController, syncStallDetector);
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
          syncStallDetector.start().ifExceptionGetsHereRaiseABug();
        });
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    peerChainTracker.stop();
    eventThread.stop();
    return syncStallDetector.stop();
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
