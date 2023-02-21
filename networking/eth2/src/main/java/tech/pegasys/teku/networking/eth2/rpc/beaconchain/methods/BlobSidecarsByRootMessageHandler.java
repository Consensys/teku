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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.spec.config.Constants.MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS;

import com.google.common.base.Throwables;
import java.nio.channels.ClosedChannelException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/p2p-interface.md#blobsidecarsbyroot-v1">BlobSidecarsByRoot
 * v1</a>
 */
public class BlobSidecarsByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobSidecarsByRootRequestMessage, SignedBlobSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final UInt64 denebForkEpoch;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt64 maxRequestSize;

  public BlobSidecarsByRootMessageHandler(
      final Spec spec,
      final UInt64 denebForkEpoch,
      final CombinedChainDataClient combinedChainDataClient,
      final UInt64 maxRequestSize) {
    this.spec = spec;
    this.denebForkEpoch = denebForkEpoch;
    this.combinedChainDataClient = combinedChainDataClient;
    this.maxRequestSize = maxRequestSize;
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BlobSidecarsByRootRequestMessage message,
      final ResponseCallback<SignedBlobSidecar> callback) {

    LOG.trace("Peer {} requested BlobSidecars with blob identifiers: {}", peer.getId(), message);

    // TODO: implement rate limiting

    SafeFuture<Void> future = SafeFuture.COMPLETE;

    final UInt64 finalizedEpoch =
        combinedChainDataClient.getLatestFinalized().map(AnchorPoint::getEpoch).orElse(UInt64.ZERO);

    for (final BlobIdentifier identifier : message) {
      final Bytes32 blockRoot = identifier.getBlockRoot();
      future =
          future
              .thenCompose(__ -> validateRequestedBlockRoot(blockRoot, finalizedEpoch))
              .thenCompose(__ -> retrieveBlobSidecar(identifier))
              .thenComposeChecked(
                  maybeSidecar -> {
                    if (maybeSidecar.isEmpty()) {
                      throw new ResourceUnavailableException(
                          String.format(
                              "Blob sidecar for block root (%s) was not available", blockRoot));
                    }
                    return callback.respond(maybeSidecar.get());
                  });
    }

    future.finish(callback::completeSuccessfully, err -> handleError(callback, err));
  }

  /**
   * Validations:
   *
   * <ul>
   *   <li>A block for the block root is available.
   *   <li>The block root references a block greater than or equal to the minimum_request_epoch
   * </ul>
   */
  private SafeFuture<Void> validateRequestedBlockRoot(
      final Bytes32 blockRoot, final UInt64 finalizedEpoch) {
    return combinedChainDataClient
        .getBlockByBlockRoot(blockRoot)
        .thenAcceptChecked(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                throw new ResourceUnavailableException(
                    String.format("Block for block root (%s) couldn't be retrieved", blockRoot));
              }
              final SignedBeaconBlock block = maybeBlock.get();
              final UInt64 requestedEpoch = spec.computeEpochAtSlot(block.getSlot());
              final UInt64 minimumRequestEpoch = computeMinimumRequestEpoch(finalizedEpoch);
              if (requestedEpoch.isLessThan(minimumRequestEpoch)) {
                throw new ResourceUnavailableException(
                    String.format(
                        "Block root (%s) references a block earlier than the minimum_request_epoch (%s)",
                        blockRoot, minimumRequestEpoch));
              }
            });
  }

  private UInt64 computeMinimumRequestEpoch(final UInt64 finalizedEpoch) {
    final UInt64 currentEpoch = combinedChainDataClient.getCurrentEpoch();
    return finalizedEpoch
        .max(currentEpoch.minusMinZero(MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS))
        .max(denebForkEpoch);
  }

  private SafeFuture<Optional<SignedBlobSidecar>> retrieveBlobSidecar(
      final BlobIdentifier identifier) {
    return combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(
        identifier.getBlockRoot(), identifier.getIndex());
  }

  private void handleError(
      final ResponseCallback<SignedBlobSidecar> callback, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace("Rejecting blob sidecars by root request", error);
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested blob sidecars", error);
      } else {
        LOG.error("Failed to process blob sidecars by root request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }
}
