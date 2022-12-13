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

package tech.pegasys.teku.networking.eth2.peers;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public interface Eth2Peer extends Peer, SyncSource {
  static Eth2Peer create(
      final Peer peer,
      final BeaconChainMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerChainValidator peerChainValidator,
      final RateTracker blockRequestTracker,
      final RateTracker blobsSidecarsRequestTracker,
      final RateTracker requestTracker) {
    return new DefaultEth2Peer(
        peer,
        rpcMethods,
        statusMessageFactory,
        metadataMessagesFactory,
        peerChainValidator,
        blockRequestTracker,
        blobsSidecarsRequestTracker,
        requestTracker);
  }

  void updateStatus(PeerStatus status);

  void updateMetadataSeqNumber(UInt64 seqNumber);

  void subscribeInitialStatus(PeerStatusSubscriber subscriber);

  void subscribeStatusUpdates(PeerStatusSubscriber subscriber);

  PeerStatus getStatus();

  Optional<SszBitvector> getRemoteAttestationSubnets();

  UInt64 finalizedEpoch();

  Checkpoint finalizedCheckpoint();

  int getOutstandingRequests();

  boolean hasStatus();

  SafeFuture<PeerStatus> sendStatus();

  SafeFuture<Void> sendGoodbye(UInt64 reason);

  SafeFuture<Void> requestBlocksByRoot(
      List<Bytes32> blockRoots, RpcResponseListener<SignedBeaconBlock> listener)
      throws RpcException;

  SafeFuture<Optional<SignedBeaconBlock>> requestBlockBySlot(UInt64 slot);

  SafeFuture<Optional<SignedBeaconBlock>> requestBlockByRoot(Bytes32 blockRoot);

  SafeFuture<MetadataMessage> requestMetadata();

  <I extends RpcRequest, O extends SszData> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final I request);

  boolean wantToReceiveBlocks(ResponseCallback<SignedBeaconBlock> callback, long blocksCount);

  boolean wantToReceiveBlobsSidecars(
      ResponseCallback<BlobsSidecar> callback, long blobsSidecarsCount);

  boolean wantToMakeRequest();

  SafeFuture<UInt64> sendPing();

  int getUnansweredPingCount();

  interface PeerStatusSubscriber {
    void onPeerStatus(final PeerStatus initialStatus);
  }
}
