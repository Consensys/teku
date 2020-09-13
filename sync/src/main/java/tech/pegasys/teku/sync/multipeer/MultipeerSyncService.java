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

package tech.pegasys.teku.sync.multipeer;

import com.google.common.eventbus.EventBus;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.sync.SyncingStatus;
import tech.pegasys.teku.sync.gossip.BlockManager;
import tech.pegasys.teku.sync.gossip.FetchRecentBlocksService;
import tech.pegasys.teku.sync.multipeer.batches.BatchFactory;
import tech.pegasys.teku.sync.multipeer.chains.FinalizedTargetChainSelector;
import tech.pegasys.teku.util.config.Constants;

public class MultipeerSyncService extends Service implements SyncService {

  private final EventThread eventThread;
  private final BlockManager blockManager;
  private final RecentChainData recentChainData;
  private final PeerChainTracker peerChainTracker;

  MultipeerSyncService(
      final EventThread eventThread,
      final BlockManager blockManager,
      final RecentChainData recentChainData,
      final PeerChainTracker peerChainTracker) {
    this.eventThread = eventThread;
    this.blockManager = blockManager;
    this.recentChainData = recentChainData;
    this.peerChainTracker = peerChainTracker;
  }

  public static MultipeerSyncService create(
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner asyncRunner,
      final EventBus eventBus,
      final RecentChainData recentChainData,
      final P2PNetwork<Eth2Peer> p2pNetwork,
      final BlockImporter blockImporter) {
    final EventThread eventThread = new AsyncRunnerEventThread("sync", asyncRunnerFactory);

    final PendingPool<SignedBeaconBlock> pendingBlocks = PendingPool.createForBlocks();
    final FutureItems<SignedBeaconBlock> futureBlocks =
        new FutureItems<>(SignedBeaconBlock::getSlot);
    final FetchRecentBlocksService recentBlockFetcher =
        FetchRecentBlocksService.create(asyncRunner, p2pNetwork, pendingBlocks);
    BlockManager blockManager =
        BlockManager.create(
            eventBus,
            pendingBlocks,
            futureBlocks,
            recentBlockFetcher,
            recentChainData,
            blockImporter);

    final FinalizedSync finalizedSync =
        new FinalizedSync(
            eventThread,
            recentChainData,
            new BatchImporter(blockImporter, asyncRunner),
            new BatchFactory(eventThread),
            Constants.SYNC_BATCH_SIZE);
    final SyncController syncController =
        new SyncController(
            eventThread, new FinalizedTargetChainSelector(recentChainData), finalizedSync);
    final PeerChainTracker peerChainTracker =
        new PeerChainTracker(eventThread, p2pNetwork, syncController);
    return new MultipeerSyncService(eventThread, blockManager, recentChainData, peerChainTracker);
  }

  @Override
  protected SafeFuture<?> doStart() {
    // We shouldn't start syncing until we have reached genesis.
    // There are also no valid blocks until we've reached genesis so no point in gossipping and
    // queuing them
    recentChainData.subscribeStoreInitialized(
        () -> {
          eventThread.start();
          blockManager.start().reportExceptions();
          peerChainTracker.start();
        });
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    peerChainTracker.stop();
    eventThread.stop();
    return blockManager.stop();
  }

  @Override
  public SyncingStatus getSyncStatus() {
    return new SyncingStatus(false, UInt64.ZERO);
  }

  @Override
  public boolean isSyncActive() {
    return false;
  }

  @Override
  public long subscribeToSyncChanges(final SyncSubscriber subscriber) {
    return 0;
  }

  @Override
  public void unsubscribeFromSyncChanges(final long subscriberId) {}
}
