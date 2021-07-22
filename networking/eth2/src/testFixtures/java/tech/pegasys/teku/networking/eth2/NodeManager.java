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

package tech.pegasys.teku.networking.eth2;

import java.util.List;
import java.util.function.Consumer;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelExceptionHandler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class NodeManager {
  private static final Spec DEFAULT_SPEC = TestSpecFactory.createMinimalPhase0();

  private final BlockGossipChannel blockGossipChannel;
  private final RecentChainData storageClient;
  private final BeaconChainUtil chainUtil;
  private final Eth2P2PNetwork eth2P2PNetwork;

  private NodeManager(
      final BlockGossipChannel blockGossipChannel,
      final RecentChainData storageClient,
      final BeaconChainUtil chainUtil,
      final Eth2P2PNetwork eth2P2PNetwork) {
    this.blockGossipChannel = blockGossipChannel;
    this.storageClient = storageClient;
    this.chainUtil = chainUtil;
    this.eth2P2PNetwork = eth2P2PNetwork;
  }

  public static NodeManager create(
      final Spec spec, Eth2P2PNetworkFactory networkFactory, final List<BLSKeyPair> validatorKeys)
      throws Exception {
    return create(spec, networkFactory, validatorKeys, c -> {});
  }

  @Deprecated
  public static NodeManager create(
      Eth2P2PNetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    return create(DEFAULT_SPEC, networkFactory, validatorKeys, configureNetwork);
  }

  public static NodeManager create(
      final Spec spec,
      Eth2P2PNetworkFactory networkFactory,
      final List<BLSKeyPair> validatorKeys,
      Consumer<Eth2P2PNetworkBuilder> configureNetwork)
      throws Exception {
    final EventChannels eventChannels =
        EventChannels.createSyncChannels(
            ChannelExceptionHandler.THROWING_HANDLER, new NoOpMetricsSystem());
    final RecentChainData storageClient = MemoryOnlyRecentChainData.create(spec);

    final BeaconChainUtil chainUtil = BeaconChainUtil.create(spec, storageClient, validatorKeys);
    chainUtil.initializeStorage();

    final Eth2P2PNetworkBuilder networkBuilder =
        networkFactory
            .builder()
            .spec(spec)
            .eventChannels(eventChannels)
            .recentChainData(storageClient);

    configureNetwork.accept(networkBuilder);

    final BlockGossipChannel blockGossipChannel =
        eventChannels.getPublisher(BlockGossipChannel.class);

    final Eth2P2PNetwork eth2P2PNetwork = networkBuilder.startNetwork();
    return new NodeManager(blockGossipChannel, storageClient, chainUtil, eth2P2PNetwork);
  }

  public SafeFuture<Peer> connect(final NodeManager peer) {
    final PeerAddress peerAddress =
        eth2P2PNetwork.createPeerAddress(peer.network().getNodeAddress());
    return eth2P2PNetwork.connect(peerAddress);
  }

  public BeaconChainUtil chainUtil() {
    return chainUtil;
  }

  public Eth2P2PNetwork network() {
    return eth2P2PNetwork;
  }

  public RecentChainData storageClient() {
    return storageClient;
  }

  public void gossipBlock(final SignedBeaconBlock block) {
    blockGossipChannel.publishBlock(block);
  }
}
