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

package tech.pegasys.artemis.networking.eth2.rpc.beaconchain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.eth2.peers.PeerLookup;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRangeMessageHandler;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRootMessageHandler;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.GoodbyeMessageHandler;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageHandler;
import tech.pegasys.artemis.networking.eth2.rpc.core.Eth2RpcMethod;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;

public class BeaconChainMethods {
  private static final String STATUS = "/eth2/beacon_chain/req/status/1";
  private static final String GOODBYE = "/eth2/beacon_chain/req/goodbye/1";
  private static final String BEACON_BLOCKS_BY_ROOT =
      "/eth2/beacon_chain/req/beacon_blocks_by_root/1";
  private static final String BEACON_BLOCKS_BY_RANGE =
      "/eth2/beacon_chain/req/beacon_blocks_by_range/1";

  private final Eth2RpcMethod<StatusMessage, StatusMessage> status;
  private final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye;
  private final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      beaconBlocksByRoot;
  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      beaconBlocksByRange;

  private final Collection<Eth2RpcMethod<?, ?>> allMethods;

  private BeaconChainMethods(
      final Eth2RpcMethod<StatusMessage, StatusMessage> status,
      final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye,
      final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot,
      final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
          beaconBlocksByRange) {
    this.status = status;
    this.goodBye = goodBye;
    this.beaconBlocksByRoot = beaconBlocksByRoot;
    this.beaconBlocksByRange = beaconBlocksByRange;
    allMethods = List.of(status, goodBye, beaconBlocksByRoot, beaconBlocksByRange);
  }

  public static BeaconChainMethods create(
      final PeerLookup peerLookup,
      final CombinedChainDataClient combinedChainDataClient,
      final ChainStorageClient chainStorageClient,
      final MetricsSystem metricsSystem,
      final StatusMessageFactory statusMessageFactory) {
    return new BeaconChainMethods(
        createStatus(statusMessageFactory, peerLookup),
        createGoodBye(metricsSystem, peerLookup),
        createBeaconBlocksByRoot(chainStorageClient, peerLookup),
        createBeaconBlocksByRange(combinedChainDataClient, peerLookup));
  }

  private static Eth2RpcMethod<StatusMessage, StatusMessage> createStatus(
      final StatusMessageFactory statusMessageFactory, final PeerLookup peerLookup) {
    final StatusMessageHandler statusHandler = new StatusMessageHandler(statusMessageFactory);
    return new Eth2RpcMethod<>(
        STATUS,
        RpcEncoding.SSZ,
        StatusMessage.class,
        StatusMessage.class,
        true,
        statusHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> createGoodBye(
      final MetricsSystem metricsSystem, final PeerLookup peerLookup) {
    final GoodbyeMessageHandler goodbyeHandler = new GoodbyeMessageHandler(metricsSystem);
    return new Eth2RpcMethod<>(
        GOODBYE,
        RpcEncoding.SSZ,
        GoodbyeMessage.class,
        GoodbyeMessage.class,
        false,
        goodbyeHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRoot(
          final ChainStorageClient chainStorageClient, final PeerLookup peerLookup) {
    final BeaconBlocksByRootMessageHandler beaconBlocksByRootHandler =
        new BeaconBlocksByRootMessageHandler(chainStorageClient);
    return new Eth2RpcMethod<>(
        BEACON_BLOCKS_BY_ROOT,
        RpcEncoding.SSZ,
        BeaconBlocksByRootRequestMessage.class,
        SignedBeaconBlock.class,
        true,
        beaconBlocksByRootHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRange(
          final CombinedChainDataClient combinedChainDataClient, final PeerLookup peerLookup) {

    final BeaconBlocksByRangeMessageHandler beaconBlocksByRangeHandler =
        new BeaconBlocksByRangeMessageHandler(combinedChainDataClient);
    return new Eth2RpcMethod<>(
        BEACON_BLOCKS_BY_RANGE,
        RpcEncoding.SSZ,
        BeaconBlocksByRangeRequestMessage.class,
        SignedBeaconBlock.class,
        true,
        beaconBlocksByRangeHandler,
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
}
