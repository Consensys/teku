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

package tech.pegasys.teku.networking.eth2.peers;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

public interface Eth2Peer extends Peer, SyncSource {
  static Eth2Peer create(
      final Peer peer,
      final BeaconChainMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerChainValidator peerChainValidator,
      final RateTracker blockRequestTracker,
      final RateTracker requestTracker) {
    return new DefaultEth2Peer(
        peer,
        rpcMethods,
        statusMessageFactory,
        metadataMessagesFactory,
        peerChainValidator,
        blockRequestTracker,
        requestTracker);
  }

  void updateStatus(PeerStatus status);

  void updateMetadataSeqNumber(UInt64 seqNumber);

  void subscribeInitialStatus(PeerStatusSubscriber subscriber);

  void subscribeStatusUpdates(PeerStatusSubscriber subscriber);

  PeerStatus getStatus();

  Optional<Bitvector> getRemoteAttestationSubnets();

  UInt64 finalizedEpoch();

  Checkpoint finalizedCheckpoint();

  int getOutstandingRequests();

  boolean hasStatus();

  SafeFuture<PeerStatus> sendStatus();

  SafeFuture<Void> sendGoodbye(UInt64 reason);

  SafeFuture<Void> requestBlocksByRoot(
      List<Bytes32> blockRoots, ResponseStreamListener<SignedBeaconBlock> listener)
      throws RpcException;

  SafeFuture<Optional<SignedBeaconBlock>> requestBlockBySlot(UInt64 slot);

  SafeFuture<Optional<SignedBeaconBlock>> requestBlockByRoot(Bytes32 blockRoot);

  SafeFuture<MetadataMessage> requestMetadata();

  <I extends RpcRequest, O> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final I request);

  boolean wantToReceiveObjects(ResponseCallback<SignedBeaconBlock> callback, long objectCount);

  boolean wantToMakeRequest();

  SafeFuture<UInt64> sendPing();

  int getOutstandingPings();

  interface PeerStatusSubscriber {
    void onPeerStatus(final PeerStatus initialStatus);
  }
}
