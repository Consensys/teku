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

package tech.pegasys.teku.beacon.sync.gossip;

import com.google.common.base.Throwables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.gossip.FetchBlockResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;

public class Eip4844FetchBlockTask extends FetchBlockTask {
  private static final Logger LOG = LogManager.getLogger();

  Eip4844FetchBlockTask(final P2PNetwork<Eth2Peer> eth2Network, final Bytes32 blockRoot) {
    super(eth2Network, blockRoot);
  }

  public static Eip4844FetchBlockTask create(
      final P2PNetwork<Eth2Peer> eth2Network, final Bytes32 blockRoot) {
    return new Eip4844FetchBlockTask(eth2Network, blockRoot);
  }

  @Override
  public SafeFuture<FetchBlockResult> fetchBlock(final Eth2Peer peer, final Bytes32 blockRoot) {
    return peer.requestBlockAndBlobsSidecarByRoot(blockRoot)
        .thenApply(
            maybeBlockAndBlobsSidecar ->
                maybeBlockAndBlobsSidecar
                    .map(FetchBlockResult::createSuccessful)
                    .orElseGet(() -> FetchBlockResult.createFailed(Status.FETCH_FAILED)))
        .exceptionallyCompose(
            throwable -> {
              final Throwable rootException = Throwables.getRootCause(throwable);
              if (rootException instanceof RpcException) {
                final RpcException rpcException = (RpcException) rootException;
                // based on behaviour described at
                // https://github.com/ethereum/consensus-specs/blob/dev/specs/eip4844/p2p-interface.md#beaconblocksbyroot-v2
                if (rpcException.getResponseCode() == RpcResponseStatus.RESOURCE_UNAVAILABLE) {
                  LOG.trace(
                      "Block root may reference a block and blobs sidecar earlier than the minimum_request_epoch. Will attempt requesting via the old RPC method.");
                  return super.fetchBlock(peer, blockRoot);
                }
              }
              return SafeFuture.failedFuture(throwable);
            });
  }
}
