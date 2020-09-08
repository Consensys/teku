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

package tech.pegasys.teku.sync.singlepeer;

import com.google.common.eventbus.EventBus;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.SyncService;
import tech.pegasys.teku.sync.gossip.BlockManager;
import tech.pegasys.teku.sync.gossip.FetchRecentBlocksService;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class SinglePeerSyncServiceFactory {
  public static SyncService create(
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final EventChannels eventChannels,
      final EventBus eventBus,
      final P2PNetwork<Eth2Peer> p2pNetwork,
      final RecentChainData recentChainData,
      final BlockImporter blockImporter) {
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
    SyncManager syncManager =
        SyncManager.create(asyncRunner, p2pNetwork, recentChainData, blockImporter, metricsSystem);
    final SinglePeerSyncService syncService =
        new SinglePeerSyncService(blockManager, syncManager, recentChainData);
    eventChannels
        .subscribe(SlotEventsChannel.class, blockManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingBlocks);
    return syncService;
  }
}
