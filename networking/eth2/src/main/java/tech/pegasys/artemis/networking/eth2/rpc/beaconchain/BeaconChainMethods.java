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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
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
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcMessageHandler;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcMethod;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcMethods;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;

public class BeaconChainMethods {
  public static final RpcMethod<StatusMessage, StatusMessage> STATUS =
      new RpcMethod<>(
          "/eth2/beacon_chain/req/status/1",
          RpcEncoding.SSZ,
          StatusMessage.class,
          StatusMessage.class);
  public static final RpcMethod<GoodbyeMessage, GoodbyeMessage> GOODBYE =
      new RpcMethod<>(
          "/eth2/beacon_chain/req/goodbye/1",
          RpcEncoding.SSZ,
          GoodbyeMessage.class,
          GoodbyeMessage.class);
  public static final RpcMethod<BeaconBlocksByRootRequestMessage, BeaconBlock>
      BEACON_BLOCKS_BY_ROOT =
          new RpcMethod<>(
              "/eth2/beacon_chain/req/beacon_blocks_by_root/1",
              RpcEncoding.SSZ,
              BeaconBlocksByRootRequestMessage.class,
              BeaconBlock.class);
  public static final RpcMethod<BeaconBlocksByRangeRequestMessage, BeaconBlock>
      BEACON_BLOCKS_BY_RANGE =
          new RpcMethod<>(
              "/eth2/beacon_chain/req/beacon_blocks_by_range/1",
              RpcEncoding.SSZ,
              BeaconBlocksByRangeRequestMessage.class,
              BeaconBlock.class);

  public static RpcMethods createRpcMethods(
      PeerLookup peerLookup,
      final CombinedChainDataClient combinedChainDataClient,
      final ChainStorageClient chainStorageClient,
      final MetricsSystem metricsSystem,
      final StatusMessageFactory statusMessageFactory) {
    final StatusMessageHandler statusHandler = new StatusMessageHandler(statusMessageFactory);
    final GoodbyeMessageHandler goodbyeHandler = new GoodbyeMessageHandler(metricsSystem);
    final BeaconBlocksByRootMessageHandler beaconBlocksByRootHandler =
        new BeaconBlocksByRootMessageHandler(chainStorageClient);
    final BeaconBlocksByRangeMessageHandler beaconBlocksByRangeHandler =
        new BeaconBlocksByRangeMessageHandler(combinedChainDataClient);
    return new RpcMethods(
        new RpcMessageHandler<>(STATUS, peerLookup, statusHandler),
        new RpcMessageHandler<>(GOODBYE, peerLookup, goodbyeHandler).setCloseNotification(),
        new RpcMessageHandler<>(BEACON_BLOCKS_BY_ROOT, peerLookup, beaconBlocksByRootHandler),
        new RpcMessageHandler<>(BEACON_BLOCKS_BY_RANGE, peerLookup, beaconBlocksByRangeHandler));
  }
}
