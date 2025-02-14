/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.beacon.sync.fetch;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;

public class FetchExecutionPayloadTask
    extends AbstractFetchTask<Bytes32, Optional<SignedExecutionPayloadEnvelope>> {

  private static final Logger LOG = LogManager.getLogger();

  private final Bytes32 blockRoot;

  public FetchExecutionPayloadTask(
      final P2PNetwork<Eth2Peer> eth2Network,
      final Optional<Eth2Peer> preferredPeer,
      final Bytes32 blockRoot) {
    super(eth2Network, preferredPeer);
    this.blockRoot = blockRoot;
  }

  @Override
  public Bytes32 getKey() {
    return blockRoot;
  }

  @Override
  SafeFuture<FetchResult<Optional<SignedExecutionPayloadEnvelope>>> fetch(final Eth2Peer peer) {
    return peer.requestExecutionPayloadEnvelopeByRoot(blockRoot)
        .thenApply(
            maybeExecutionPayload ->
                maybeExecutionPayload
                    .map(
                        executionPayload ->
                            FetchResult.createSuccessful(peer, Optional.of(executionPayload)))
                    // missing execution payload
                    .orElseGet(() -> FetchResult.createSuccessful(peer, Optional.empty())))
        .exceptionally(
            err -> {
              LOG.warn(
                  String.format(
                      "Failed to fetch execution payload by block root %s from peer %s",
                      blockRoot, peer.getId()),
                  err);
              return FetchResult.createFailed(peer, Status.FETCH_FAILED);
            });
  }
}
