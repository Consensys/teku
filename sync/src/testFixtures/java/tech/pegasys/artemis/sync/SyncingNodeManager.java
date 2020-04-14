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

package tech.pegasys.artemis.sync;

import static tech.pegasys.artemis.events.TestExceptionHandler.TEST_EXCEPTION_HANDLER;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.function.Consumer;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.events.EventChannels;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.artemis.networking.p2p.network.PeerAddress;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.statetransition.forkchoice.ForkChoice;
import tech.pegasys.artemis.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.artemis.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.time.channels.SlotEventsChannel;

public class SyncingNodeManager {
  private final EventBus eventBus;
  private final EventChannels eventChannels;
  private final RecentChainData storageClient;
  private final BeaconChainUtil chainUtil;
  private final Eth2Network eth2Network;
  private final SyncService syncService;

  private SyncingNodeManager(
      final EventBus eventBus,
      final EventChannels eventChannels,
      final RecentChainData storageClient,
      final BeaconChainUtil chainUtil,
      final Eth2Network eth2Network,
      final SyncService syncService) {
    this.eventBus = eventBus;
    this.eventChannels = eventChannels;
    this.storageClient = storageClient;
    this.chainUtil = chainUtil;
    this.eth2Network = eth2Network;
    this.syncService = syncService;
  }

  public static SyncingNodeManager create(
      Eth2NetworkFactory networkFactory, final List<BLSKeyPair> validatorKeys) throws Exception {
    return create(networkFactory, validatorKeys, c -> {});
  }

  public static SyncingNodeManager create(
      Eth2NetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    final EventBus eventBus = new EventBus();
    final EventChannels eventChannels =
        EventChannels.createSyncChannels(TEST_EXCEPTION_HANDLER, new NoOpMetricsSystem());
    final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
    final Eth2P2PNetworkBuilder networkBuilder =
        networkFactory.builder().eventBus(eventBus).recentChainData(recentChainData);

    configureNetwork.accept(networkBuilder);

    final Eth2Network eth2Network = networkBuilder.startNetwork();

    final BeaconChainUtil chainUtil = BeaconChainUtil.create(recentChainData, validatorKeys);
    chainUtil.initializeStorage();

    ForkChoice forkChoice = new ForkChoice(recentChainData, new StateTransition());
    BlockImporter blockImporter = new BlockImporter(recentChainData, forkChoice, eventBus);
    final PendingPool<SignedBeaconBlock> pendingBlocks = PendingPool.createForBlocks(eventBus);
    final FutureItems<SignedBeaconBlock> futureBlocks =
        new FutureItems<>(SignedBeaconBlock::getSlot);
    final FetchRecentBlocksService recentBlockFetcher =
        FetchRecentBlocksService.create(eth2Network, pendingBlocks);
    BlockPropagationManager blockPropagationManager =
        BlockPropagationManager.create(
            eventBus,
            pendingBlocks,
            futureBlocks,
            recentBlockFetcher,
            recentChainData,
            blockImporter);

    SyncManager syncManager = SyncManager.create(eth2Network, recentChainData, blockImporter);
    SyncService syncService =
        new DefaultSyncService(blockPropagationManager, syncManager, recentChainData);

    eventChannels
        .subscribe(SlotEventsChannel.class, blockPropagationManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingBlocks);

    syncService.start().join();

    return new SyncingNodeManager(
        eventBus, eventChannels, recentChainData, chainUtil, eth2Network, syncService);
  }

  public SafeFuture<Peer> connect(final SyncingNodeManager peer) {
    final PeerAddress peerAddress = eth2Network.createPeerAddress(peer.network().getNodeAddress());
    return eth2Network.connect(peerAddress);
  }

  public EventChannels eventChannels() {
    return eventChannels;
  }

  public EventBus eventBus() {
    return eventBus;
  }

  public BeaconChainUtil chainUtil() {
    return chainUtil;
  }

  public Eth2Network network() {
    return eth2Network;
  }

  public RecentChainData storageClient() {
    return storageClient;
  }

  public SyncService syncService() {
    return syncService;
  }

  public void setSlot(UnsignedLong slot) {
    eventChannels().getPublisher(SlotEventsChannel.class).onSlot(slot);
    chainUtil().setSlot(slot);
  }
}
