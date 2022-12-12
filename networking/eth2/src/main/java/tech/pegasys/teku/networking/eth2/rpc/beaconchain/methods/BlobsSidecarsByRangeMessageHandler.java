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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobsSidecarsByRangeMessageHandler
    extends PeerRequiredLocalMessageHandler<BlobsSidecarsByRangeRequestMessage, BlobsSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt64 maxRequestSize;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalBlobsSidecarsRequestedCounter;

  public BlobsSidecarsByRangeMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient combinedChainDataClient,
      final UInt64 maxRequestSize) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    this.maxRequestSize = maxRequestSize;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blobs_sidecars_by_range_requests_total",
            "Total number of blobs sidecars by range requests received",
            "status");
    totalBlobsSidecarsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blobs_sidecars_by_range_requested_sidecars_total",
            "Total number of sidecars requested in accepted blobs sidecars by range requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final BlobsSidecarsByRangeRequestMessage request) {
    // TODO: implement
    return Optional.empty();
  }

  @Override
  protected void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BlobsSidecarsByRangeRequestMessage message,
      final ResponseCallback<BlobsSidecar> callback) {
    // TODO: implement
    LOG.trace(
        "Peer {} requested {} BeaconBlocks starting at slot {}.",
        peer.getId(),
        message.getCount(),
        message.getStartSlot());
    requestCounter.labels("ok").inc();
    totalBlobsSidecarsRequestedCounter.inc(message.getCount().longValue());
    callback.completeWithUnexpectedError(new UnsupportedOperationException("Not yet implemented"));
  }
}
