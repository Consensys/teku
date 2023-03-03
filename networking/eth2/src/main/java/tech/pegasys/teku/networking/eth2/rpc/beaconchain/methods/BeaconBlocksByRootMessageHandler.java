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

import com.google.common.annotations.VisibleForTesting;
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
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.InvalidRpcMethodVersion;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconBlocksByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData storageClient;
  private final Counter totalBlocksRequestedCounter;
  private final LabelledMetric<Counter> requestCounter;

  public BeaconBlocksByRootMessageHandler(
      final Spec spec, final MetricsSystem metricsSystem, final RecentChainData storageClient) {
    this.spec = spec;
    this.storageClient = storageClient;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blocks_by_root_requests_total",
            "Total number of blocks by root requests received",
            "status");
    totalBlocksRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blocks_by_root_requested_blocks_total",
            "Total number of blocks requested in accepted blocks by root requests from peers");
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BeaconBlocksByRootRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    LOG.trace("Peer {} requested BeaconBlocks with roots: {}", peer.getId(), message);
    if (storageClient.getStore() != null) {
      SafeFuture<Void> future = SafeFuture.COMPLETE;
      if (!peer.allowedToMakeRequest() || !peer.allowedToReceiveBlocks(callback, message.size())) {
        requestCounter.labels("rate_limited").inc();
        return;
      }

      requestCounter.labels("ok").inc();
      totalBlocksRequestedCounter.inc(message.size());
      for (SszBytes32 blockRoot : message) {
        future =
            future.thenCompose(
                __ ->
                    storageClient
                        .getStore()
                        .retrieveSignedBlock(blockRoot.get())
                        .thenCompose(
                            block -> {
                              final Optional<RpcException> validationResult =
                                  block.flatMap(b -> validateResponse(protocolId, b));
                              if (validationResult.isPresent()) {
                                return SafeFuture.failedFuture(validationResult.get());
                              }
                              return block.map(callback::respond).orElse(SafeFuture.COMPLETE);
                            }));
      }
      future.finish(callback::completeSuccessfully, err -> handleError(callback, err));
    } else {
      requestCounter.labels("ok").inc();
      callback.completeSuccessfully();
    }
  }

  private void handleError(
      final ResponseCallback<SignedBeaconBlock> callback, final Throwable error) {
    final Throwable rootCause = Throwables.getRootCause(error);
    if (rootCause instanceof RpcException) {
      LOG.trace("Rejecting beacon blocks by root request", error); // Keep full context
      callback.completeWithErrorResponse((RpcException) rootCause);
    } else {
      if (rootCause instanceof StreamClosedException
          || rootCause instanceof ClosedChannelException) {
        LOG.trace("Stream closed while sending requested blocks", error);
      } else {
        LOG.error("Failed to process blocks by root request", error);
      }
      callback.completeWithUnexpectedError(error);
    }
  }

  @VisibleForTesting
  Optional<RpcException> validateResponse(
      final String protocolId, final SignedBeaconBlock response) {
    final int version = BeaconChainMethodIds.extractBeaconBlocksByRootVersion(protocolId);
    final SpecMilestone milestoneAtResponse =
        spec.getForkSchedule().getSpecMilestoneAtSlot(response.getSlot());
    final boolean isAltairActive = milestoneAtResponse.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR);

    if (version == 1 && isAltairActive) {
      return Optional.of(
          new InvalidRpcMethodVersion("Must request altair blocks using v2 protocol"));
    }

    return Optional.empty();
  }
}
