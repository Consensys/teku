/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRootMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.GoodbyeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.async.AsyncRunner;

public class BeaconChainMethods {
  private static final String STATUS = "/eth2/beacon_chain/req/status/1";
  private static final String GOODBYE = "/eth2/beacon_chain/req/goodbye/1";
  private static final String BEACON_BLOCKS_BY_ROOT =
      "/eth2/beacon_chain/req/beacon_blocks_by_root/1";
  private static final String BEACON_BLOCKS_BY_RANGE =
      "/eth2/beacon_chain/req/beacon_blocks_by_range/1";
  private static final String GET_METADATA = "/eth2/beacon_chain/req/metadata/1";

  private final Eth2RpcMethod<StatusMessage, StatusMessage> status;
  private final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye;
  private final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      beaconBlocksByRoot;
  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      beaconBlocksByRange;
  private final Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata;

  private final Collection<Eth2RpcMethod<?, ?>> allMethods;

  private BeaconChainMethods(
      final Eth2RpcMethod<StatusMessage, StatusMessage> status,
      final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye,
      final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot,
      final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> beaconBlocksByRange,
      final Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata) {
    this.status = status;
    this.goodBye = goodBye;
    this.beaconBlocksByRoot = beaconBlocksByRoot;
    this.beaconBlocksByRange = beaconBlocksByRange;
    this.getMetadata = getMetadata;
    allMethods = List.of(status, goodBye, beaconBlocksByRoot, beaconBlocksByRange, getMetadata);
  }

  public static BeaconChainMethods create(
      final AsyncRunner asyncRunner,
      final PeerLookup peerLookup,
      final CombinedChainDataClient combinedChainDataClient,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessageFactory metadataMessageFactory,
      final RpcEncoding rpcEncoding) {
    return new BeaconChainMethods(
        createStatus(asyncRunner, statusMessageFactory, peerLookup, rpcEncoding),
        createGoodBye(asyncRunner, metricsSystem, peerLookup, rpcEncoding),
        createBeaconBlocksByRoot(asyncRunner, recentChainData, peerLookup, rpcEncoding),
        createBeaconBlocksByRange(asyncRunner, combinedChainDataClient, peerLookup, rpcEncoding),
        createMetadata(asyncRunner, metadataMessageFactory, peerLookup, rpcEncoding));
  }

  private static Eth2RpcMethod<StatusMessage, StatusMessage> createStatus(
      final AsyncRunner asyncRunner,
      final StatusMessageFactory statusMessageFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final StatusMessageHandler statusHandler = new StatusMessageHandler(statusMessageFactory);
    return new Eth2RpcMethod<>(
        asyncRunner,
        STATUS,
        rpcEncoding,
        StatusMessage.class,
        StatusMessage.class,
        true,
        statusHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> createGoodBye(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final GoodbyeMessageHandler goodbyeHandler = new GoodbyeMessageHandler(metricsSystem);
    return new Eth2RpcMethod<>(
        asyncRunner,
        GOODBYE,
        rpcEncoding,
        GoodbyeMessage.class,
        GoodbyeMessage.class,
        false,
        goodbyeHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRoot(
          final AsyncRunner asyncRunner,
          final RecentChainData recentChainData,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {
    final BeaconBlocksByRootMessageHandler beaconBlocksByRootHandler =
        new BeaconBlocksByRootMessageHandler(recentChainData);
    return new Eth2RpcMethod<>(
        asyncRunner,
        BEACON_BLOCKS_BY_ROOT,
        rpcEncoding,
        BeaconBlocksByRootRequestMessage.class,
        SignedBeaconBlock.class,
        true,
        beaconBlocksByRootHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRange(
          final AsyncRunner asyncRunner,
          final CombinedChainDataClient combinedChainDataClient,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {

    final BeaconBlocksByRangeMessageHandler beaconBlocksByRangeHandler =
        new BeaconBlocksByRangeMessageHandler(combinedChainDataClient);
    return new Eth2RpcMethod<>(
        asyncRunner,
        BEACON_BLOCKS_BY_RANGE,
        rpcEncoding,
        BeaconBlocksByRangeRequestMessage.class,
        SignedBeaconBlock.class,
        true,
        beaconBlocksByRangeHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<EmptyMessage, MetadataMessage> createMetadata(
      final AsyncRunner asyncRunner,
      final MetadataMessageFactory metadataMessageFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    MetadataMessageHandler messageHandler = new MetadataMessageHandler(metadataMessageFactory);
    return new Eth2RpcMethod<EmptyMessage, MetadataMessage>(
        asyncRunner,
        GET_METADATA,
        rpcEncoding,
        EmptyMessage.class,
        MetadataMessage.class,
        true,
        messageHandler,
        peerLookup);
  }

  public Collection<RpcMethod> all() {
    return Collections.unmodifiableCollection(allMethods);
  }

  public Eth2RpcMethod<StatusMessage, StatusMessage> status() {
    return status;
  }

  public Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye() {
    return goodBye;
  }

  public Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot() {
    return beaconBlocksByRoot;
  }

  public Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> beaconBlocksByRange() {
    return beaconBlocksByRange;
  }

  public Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata() {
    return getMetadata;
  }
}
