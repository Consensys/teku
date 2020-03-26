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
import tech.pegasys.artemis.events.EventChannels;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.artemis.networking.p2p.network.PeerAddress;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.time.channels.SlotEventsChannel;

public class SyncingNodeManager {
  private final EventBus eventBus;
  private final EventChannels eventChannels;
  private final ChainStorageClient storageClient;
  private final BeaconChainUtil chainUtil;
  private final Eth2Network eth2Network;
  private final SyncService syncService;

  private SyncingNodeManager(
      final EventBus eventBus,
      final EventChannels eventChannels,
      final ChainStorageClient storageClient,
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
    final ChainStorageClient storageClient = ChainStorageClient.memoryOnlyClient(eventBus);
    final Eth2P2PNetworkBuilder networkBuilder =
        networkFactory.builder().eventBus(eventBus).chainStorageClient(storageClient);

    configureNetwork.accept(networkBuilder);

    final Eth2Network eth2Network = networkBuilder.startNetwork();

    final BeaconChainUtil chainUtil = BeaconChainUtil.create(storageClient, validatorKeys);
    chainUtil.initializeStorage();

    BlockImporter blockImporter = new BlockImporter(storageClient, eventBus);
    BlockPropagationManager blockPropagationManager =
        BlockPropagationManager.create(eventBus, eth2Network, storageClient, blockImporter);
    SyncManager syncManager = SyncManager.create(eth2Network, storageClient, blockImporter);
    SyncService syncService =
        new DefaultSyncService(blockPropagationManager, syncManager, storageClient);

    eventChannels.subscribe(SlotEventsChannel.class, blockPropagationManager);

    syncService.start().join();

    return new SyncingNodeManager(
        eventBus, eventChannels, storageClient, chainUtil, eth2Network, syncService);
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

  public ChainStorageClient storageClient() {
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
