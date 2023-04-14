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

package tech.pegasys.teku.beacon.sync;

import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.events.TestExceptionHandler.TEST_EXCEPTION_HANDLER;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.beacon.sync.fetch.DefaultFetchTaskFactory;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.beacon.sync.forward.singlepeer.SinglePeerSyncService;
import tech.pegasys.teku.beacon.sync.forward.singlepeer.SyncManager;
import tech.pegasys.teku.beacon.sync.gossip.blocks.FetchRecentBlocksService;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportNotifications;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.StubForkChoiceNotifier;
import tech.pegasys.teku.statetransition.util.BlobSidecarPoolImpl;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityFactory;

public class SyncingNodeManager {
  private final EventChannels eventChannels;
  private final RecentChainData recentChainData;
  private final BeaconChainUtil chainUtil;
  private final Eth2P2PNetwork eth2P2PNetwork;
  private final ForwardSync syncService;
  private final BlockGossipChannel blockGossipChannel;

  private SyncingNodeManager(
      final EventChannels eventChannels,
      final RecentChainData recentChainData,
      final BeaconChainUtil chainUtil,
      final Eth2P2PNetwork eth2P2PNetwork,
      final ForwardSync syncService) {
    this.eventChannels = eventChannels;
    this.recentChainData = recentChainData;
    this.chainUtil = chainUtil;
    this.eth2P2PNetwork = eth2P2PNetwork;
    this.syncService = syncService;
    this.blockGossipChannel = eventChannels.getPublisher(BlockGossipChannel.class);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public static SyncingNodeManager create(
      final AsyncRunner asyncRunner,
      Eth2P2PNetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final TimeProvider timeProvider = new SystemTimeProvider();
    final EventChannels eventChannels =
        EventChannels.createSyncChannels(TEST_EXCEPTION_HANDLER, new NoOpMetricsSystem());
    final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(spec);

    final BeaconChainUtil chainUtil = BeaconChainUtil.create(spec, recentChainData, validatorKeys);
    chainUtil.initializeStorage();

    final MergeTransitionBlockValidator transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, recentChainData, ExecutionLayerChannel.NOOP);
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            recentChainData,
            BlobSidecarManager.NOOP,
            new StubForkChoiceNotifier(),
            transitionBlockValidator);
    final BlockImporter blockImporter =
        new BlockImporter(
            spec,
            eventChannels.getPublisher(BlockImportNotifications.class),
            recentChainData,
            forkChoice,
            WeakSubjectivityFactory.lenientValidator(),
            new ExecutionLayerChannelStub(spec, false, Optional.empty()));

    final BlockValidator blockValidator =
        new BlockValidator(
            spec, recentChainData, new GossipValidationHelper(spec, recentChainData));

    final PoolFactory poolFactory = new PoolFactory(new NoOpMetricsSystem());
    final PendingPool<SignedBeaconBlock> pendingBlocks =
        poolFactory.createPendingPoolForBlocks(spec);
    final BlobSidecarPoolImpl blobSidecarPool =
        poolFactory.createPoolForBlobSidecars(spec, timeProvider, asyncRunner, recentChainData);
    final FutureItems<SignedBeaconBlock> futureBlocks =
        FutureItems.create(SignedBeaconBlock::getSlot, mock(SettableLabelledGauge.class), "blocks");
    final Map<Bytes32, BlockImportResult> invalidBlockRoots = LimitedMap.createSynchronized(500);
    final BlockManager blockManager =
        new BlockManager(
            recentChainData,
            blockImporter,
            pendingBlocks,
            futureBlocks,
            invalidBlockRoots,
            blockValidator,
            timeProvider,
            EVENT_LOG,
            Optional.empty());

    eventChannels
        .subscribe(SlotEventsChannel.class, blockManager)
        .subscribe(BlockImportChannel.class, blockManager)
        .subscribe(BlockImportNotifications.class, blockManager)
        .subscribe(FinalizedCheckpointChannel.class, pendingBlocks)
        .subscribe(SlotEventsChannel.class, pendingBlocks)
        .subscribe(FinalizedCheckpointChannel.class, blobSidecarPool)
        .subscribe(SlotEventsChannel.class, blobSidecarPool);

    final Eth2P2PNetworkBuilder networkBuilder =
        networkFactory
            .builder()
            .spec(spec)
            .eventChannels(eventChannels)
            .recentChainData(recentChainData)
            .gossipedBlockProcessor(blockManager::validateAndImportBlock);

    configureNetwork.accept(networkBuilder);

    final Eth2P2PNetwork eth2P2PNetwork = networkBuilder.startNetwork();

    final SyncManager syncManager =
        SyncManager.create(
            asyncRunner,
            eth2P2PNetwork,
            recentChainData,
            blockImporter,
            // TODO: change to the real value when renamed and initialized
            BlobSidecarManager.NOOP,
            // TODO: change to the real value when implemented
            BlobSidecarPool.NOOP,
            new NoOpMetricsSystem(),
            spec);

    final ForwardSyncService syncService = new SinglePeerSyncService(syncManager, recentChainData);

    final FetchTaskFactory fetchBlockTaskFactory = new DefaultFetchTaskFactory(eth2P2PNetwork);

    final FetchRecentBlocksService recentBlockFetcher =
        FetchRecentBlocksService.create(
            asyncRunner, pendingBlocks, blobSidecarPool, syncService, fetchBlockTaskFactory);
    recentBlockFetcher.subscribeBlockFetched(blockManager::importBlock);
    blockManager.subscribeToReceivedBlocks(
        (block, executionOptimistic) ->
            recentBlockFetcher.cancelRecentBlockRequest(block.getRoot()));

    recentBlockFetcher.start().join();
    blockManager.start().join();
    syncService.start().join();

    return new SyncingNodeManager(
        eventChannels, recentChainData, chainUtil, eth2P2PNetwork, syncService);
  }

  public SafeFuture<Peer> connect(final SyncingNodeManager peer) {
    final PeerAddress peerAddress =
        eth2P2PNetwork.createPeerAddress(peer.network().getNodeAddress());
    return eth2P2PNetwork.connect(peerAddress);
  }

  public EventChannels eventChannels() {
    return eventChannels;
  }

  public BeaconChainUtil chainUtil() {
    return chainUtil;
  }

  public Eth2P2PNetwork network() {
    return eth2P2PNetwork;
  }

  public RecentChainData recentChainData() {
    return recentChainData;
  }

  public ForwardSync syncService() {
    return syncService;
  }

  public void setSlot(final UInt64 slot) {
    eventChannels().getPublisher(SlotEventsChannel.class).onSlot(slot);
    chainUtil().setSlot(slot);
  }

  public void gossipBlock(final SignedBeaconBlock block) {
    blockGossipChannel.publishBlock(block);
  }
}
