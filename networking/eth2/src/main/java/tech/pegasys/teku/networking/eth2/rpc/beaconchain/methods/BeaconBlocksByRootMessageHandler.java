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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import org.apache.logging.log4j.LogManager;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconBlocksByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> {
  private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger();

  private final RecentChainData storageClient;

  public BeaconBlocksByRootMessageHandler(final RecentChainData storageClient) {
    this.storageClient = storageClient;
  }

  @Override
  public void onIncomingMessage(
      final Eth2Peer peer,
      final BeaconBlocksByRootRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    LOG.trace(
        "Peer {} requested BeaconBlocks with roots: {}", peer.getId(), message.getBlockRoots());
    if (storageClient.getStore() != null) {
      SafeFuture<Void> future = SafeFuture.COMPLETE;
      if (!peer.wantToMakeRequest()
          || !peer.wantToReceiveObjects(callback, message.getBlockRoots().size())) {
        peer.disconnectCleanly(DisconnectReason.RATE_LIMITING).reportExceptions();
        return;
      }

      for (Bytes32 blockRoot : message.getBlockRoots()) {
        future =
            future.thenCompose(
                __ ->
                    storageClient
                        .getStore()
                        .retrieveSignedBlock(blockRoot)
                        .thenCompose(
                            block -> block.map(callback::respond).orElse(SafeFuture.COMPLETE)));
      }
      future.finish(callback::completeSuccessfully, callback::completeWithUnexpectedError);
    } else {
      callback.completeSuccessfully();
    }
  }
}
