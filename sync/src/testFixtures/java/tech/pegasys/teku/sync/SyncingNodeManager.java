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

package tech.pegasys.teku.sync;

import static tech.pegasys.teku.infrastructure.events.TestExceptionHandler.TEST_EXCEPTION_HANDLER;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.function.Consumer;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ForkChoiceAttestationValidator;
import tech.pegasys.teku.core.ForkChoiceBlockTasks;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2Network;
import tech.pegasys.teku.networking.eth2.Eth2NetworkFactory;
import tech.pegasys.teku.networking.eth2.Eth2NetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.SyncForkChoiceExecutor;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.ForwardSync;
import tech.pegasys.teku.sync.forward.ForwardSyncService;
import tech.pegasys.teku.sync.forward.singlepeer.SinglePeerSyncService;
import tech.pegasys.teku.sync.forward.singlepeer.SyncManager;
import tech.pegasys.teku.sync.gossip.FetchRecentBlocksService;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

public class SyncingNodeManager {
  private final EventBus eventBus;
  private final EventChannels eventChannels;
  private final RecentChainData storageClient;
  private final BeaconChainUtil chainUtil;
  private final Eth2Network eth2Network;
  private final ForwardSync syncService;

  private SyncingNodeManager(
      final EventBus eventBus,
      final EventChannels eventChannels,
      final RecentChainData storageClient,
      final BeaconChainUtil chainUtil,
      final Eth2Network eth2Network,
      final ForwardSync syncService) {
    this.eventBus = eventBus;
    this.eventChannels = eventChannels;
    this.storageClient = storageClient;
    this.chainUtil = chainUtil;
    this.eth2Network = eth2Network;
    this.syncService = syncService;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public static SyncingNodeManager create(
      final AsyncRunner asyncRunner,
      Eth2NetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    final EventBus eventBus = new EventBus();
    final EventChannels eventChannels =
        EventChannels.createSyncChannels(TEST_EXCEPTION_HANDLER, new NoOpMetricsSystem());
    final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);

    final BeaconChainUtil chainUtil = BeaconChainUtil.create(recentChainData, validatorKeys);
    chainUtil.initializeStorage();

    ForkChoice forkChoice =
        new ForkChoice(
            new ForkChoiceAttestationValidator(),
            new ForkChoiceBlockTasks(),
            new SyncForkChoiceExecutor(),
            recentChainData,
            new StateTransition());
    BlockImporter blockImporter =
        new BlockImporter(
            recentChainData, forkChoice, WeakSubjectivityValidator.lenient(), eventBus);

    BlockValidator blockValidator = new BlockValidator(recentChainData);
    final PendingPool<SignedBeaconBlock> pendingBlocks = PendingPool.createForBlocks();
    final FutureItems<SignedBeaconBlock> futureBlocks =
        FutureItems.create(SignedBeaconBlock::getSlot);
    BlockManager blockManager =
        BlockManager.create(
            eventBus, pendingBlocks, futureBlocks, recentChainData, blockImporter, blockValidator);

    eventChannels
        .subscribe(SlotEventsChannel.class, blockManager)
        .subscribe(BlockImportChannel.class, blockManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingBlocks);

    final Eth2P2PNetworkBuilder networkBuilder =
        networkFactory
            .builder()
            .eventBus(eventBus)
            .recentChainData(recentChainData)
            .gossipedBlockProcessor(blockManager::validateAndImportBlock);

    configureNetwork.accept(networkBuilder);

    final Eth2Network eth2Network = networkBuilder.startNetwork();

    final FetchRecentBlocksService recentBlockFetcher =
        FetchRecentBlocksService.create(asyncRunner, eth2Network, pendingBlocks);
    recentBlockFetcher.subscribeBlockFetched(blockManager::importBlock);
    blockManager.subscribeToReceivedBlocks(recentBlockFetcher::cancelRecentBlockRequest);

    SyncManager syncManager =
        SyncManager.create(
            asyncRunner, eth2Network, recentChainData, blockImporter, new NoOpMetricsSystem());
    ForwardSyncService syncService = new SinglePeerSyncService(syncManager, recentChainData);

    recentBlockFetcher.start().join();
    blockManager.start().join();
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

  public RecentChainData recentChainData() {
    return storageClient;
  }

  public ForwardSync syncService() {
    return syncService;
  }

  public void setSlot(UInt64 slot) {
    eventChannels().getPublisher(SlotEventsChannel.class).onSlot(slot);
    chainUtil().setSlot(slot);
  }
}
