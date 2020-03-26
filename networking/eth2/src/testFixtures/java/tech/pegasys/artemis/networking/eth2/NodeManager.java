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

package tech.pegasys.artemis.networking.eth2;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.function.Consumer;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.artemis.events.EventChannels;
import tech.pegasys.artemis.networking.eth2.Eth2NetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.artemis.networking.p2p.network.PeerAddress;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.api.DiskUpdateChannel;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

import static tech.pegasys.artemis.events.TestExceptionHandler.TEST_EXCEPTION_HANDLER;

public class NodeManager {
  private final EventBus eventBus;
  private final ChainStorageClient storageClient;
  private final BeaconChainUtil chainUtil;
  private final Eth2Network eth2Network;

  private NodeManager(
      final EventBus eventBus,
      final ChainStorageClient storageClient,
      final BeaconChainUtil chainUtil,
      final Eth2Network eth2Network) {
    this.eventBus = eventBus;
    this.storageClient = storageClient;
    this.chainUtil = chainUtil;
    this.eth2Network = eth2Network;
  }

  public static NodeManager create(
      Eth2NetworkFactory networkFactory, final List<BLSKeyPair> validatorKeys) throws Exception {
    return create(networkFactory, validatorKeys, c -> {});
  }

  public static NodeManager create(
      Eth2NetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    final EventBus eventBus = new EventBus();
    final EventChannels eventChannels =
            EventChannels.createSyncChannels(TEST_EXCEPTION_HANDLER, new NoOpMetricsSystem());
    final ChainStorageClient storageClient = ChainStorageClient.memoryOnlyClient(
            eventBus, eventChannels.getPublisher(DiskUpdateChannel.class));
    final Eth2P2PNetworkBuilder networkBuilder =
        networkFactory.builder().eventBus(eventBus).chainStorageClient(storageClient);

    configureNetwork.accept(networkBuilder);

    final Eth2Network eth2Network = networkBuilder.startNetwork();

    final BeaconChainUtil chainUtil = BeaconChainUtil.create(storageClient, validatorKeys);
    chainUtil.initializeStorage();

    return new NodeManager(eventBus, storageClient, chainUtil, eth2Network);
  }

  public SafeFuture<Peer> connect(final NodeManager peer) {
    final PeerAddress peerAddress = eth2Network.createPeerAddress(peer.network().getNodeAddress());
    return eth2Network.connect(peerAddress);
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
}
