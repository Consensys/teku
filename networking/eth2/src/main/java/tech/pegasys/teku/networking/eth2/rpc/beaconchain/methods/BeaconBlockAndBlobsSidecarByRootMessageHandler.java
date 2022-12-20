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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlockAndBlobsSidecarByRootRequestMessage;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconBlockAndBlobsSidecarByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<
        BeaconBlockAndBlobsSidecarByRootRequestMessage, SignedBeaconBlockAndBlobsSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final UInt64 eip4844ForkEpoch;
  private final RecentChainData recentChainData;
  private final Counter totalBlockAndBlobsSidecarRequestedCounter;
  private final LabelledMetric<Counter> requestCounter;

  public BeaconBlockAndBlobsSidecarByRootMessageHandler(
      final Spec spec,
      final UInt64 eip4844ForkEpoch,
      final MetricsSystem metricsSystem,
      final RecentChainData recentChainData) {
    this.spec = spec;
    this.eip4844ForkEpoch = eip4844ForkEpoch;
    this.recentChainData = recentChainData;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_block_and_blobs_sidecar_by_root_requests_total",
            "Total number of block and blobs sidecar by root requests received",
            "status");
    totalBlockAndBlobsSidecarRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_block_and_blobs_sidecar_by_root_requested_total",
            "Total number of block and blobs sidecar requested in accepted block and blobs sidecar by root requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final BeaconBlockAndBlobsSidecarByRootRequestMessage request) {

    final UInt64 finalizedEpoch = recentChainData.getFinalizedEpoch();
    final UInt64 currentEpoch =
        recentChainData
            .getCurrentEpoch()
            .orElse(UInt64.ZERO)
            .minusMinZero(MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS);
    final UInt64 minimumRequestEpoch = finalizedEpoch.max(currentEpoch).max(eip4844ForkEpoch);

    for (final SszBytes32 blockRoot : request) {
      final Optional<UInt64> requestedSlot = recentChainData.getSlotForBlockRoot(blockRoot.get());
      if (!verifyRequestedEpochIsInSupportedRange(requestedSlot, minimumRequestEpoch)) {
        requestCounter.labels("resource_unavailable").inc();
        totalBlockAndBlobsSidecarRequestedCounter.inc(request.size());
        return Optional.of(
            new ResourceUnavailableException(
                "Can't request block and blobs sidecar earlier than epoch " + minimumRequestEpoch));
      }
    }

    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BeaconBlockAndBlobsSidecarByRootRequestMessage message,
      final ResponseCallback<SignedBeaconBlockAndBlobsSidecar> callback) {
    LOG.trace("Peer {} requested BeaconBlockAndBlobsSidecar with roots: {}", peer.getId(), message);

    if (!peer.wantToMakeRequest()
        || !peer.wantToReceiveBlockAndBlobsSidecars(callback, message.size())) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalBlockAndBlobsSidecarRequestedCounter.inc(message.size());

    SafeFuture<Void> future = SafeFuture.COMPLETE;
    for (final SszBytes32 blockRoot : message) {
      future =
          future.thenCompose(
              __ ->
                  recentChainData
                      .getStore()
                      .retrieveSignedBlockAndBlobsSidecar(blockRoot.get())
                      .thenCompose(
                          blockAndBlobsSidecar ->
                              blockAndBlobsSidecar
                                  .map(callback::respond)
                                  .orElse(SafeFuture.COMPLETE)));
    }

    future.finish(callback::completeSuccessfully, err -> handleError(callback, err));
  }

  /**
   * <a href="https://github.com/ethereum/consensus-specs/pull/3154">Clarify
   * BeaconBlockAndBlobsSidecarByRoot no blob available</a>
   */
  private boolean verifyRequestedEpochIsInSupportedRange(
      final Optional<UInt64> requestedSlot, final UInt64 minimumRequestEpoch) {
    return requestedSlot
        .map(spec::computeEpochAtSlot)
        .map(blockEpoch -> blockEpoch.isGreaterThanOrEqualTo(minimumRequestEpoch))
        .orElse(true);
  }

  private void handleError(
      final ResponseCallback<SignedBeaconBlockAndBlobsSidecar> callback, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace(
          "Rejecting beacon block and blobs sidecar by root request", error); // Keep full context
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested block and blobs sidecar", error);
      } else {
        LOG.error("Failed to process block and blobs sidecar by root request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }
}
