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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.InclusionListByCommitteeRequestMessage;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.statetransition.inclusionlist.InclusionListManager;

public class InclusionListByCommitteeIndicesMessageHandler
    extends PeerRequiredLocalMessageHandler<
        InclusionListByCommitteeRequestMessage, SignedInclusionList> {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final InclusionListManager inclusionListManager;
  private final LabelledMetric<Counter> requestCounter;
  private final Counter totalInclusionListsRequestedCounter;

  public InclusionListByCommitteeIndicesMessageHandler(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final InclusionListManager inclusionListManager) {
    this.spec = spec;
    this.inclusionListManager = inclusionListManager;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_inclusion_list_by_committee_indices_requests_total",
            "Total number of inclusion list by committee indices requests received",
            "status");
    totalInclusionListsRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_inclusion_list_by_committee_indices_requested_total",
            "Total number of inclusion list requested in accepted inclusion list by committee indices requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final InclusionListByCommitteeRequestMessage request) {
    final SpecConfigEip7805 specConfig =
        SpecConfigEip7805.required(spec.atSlot(request.getSlot()).getConfig());

    final int maxRequestInclusionList = specConfig.getMaxRequestInclusionList();

    if (request.size() > maxRequestInclusionList) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              "Only a maximum of "
                  + maxRequestInclusionList
                  + " inclusion lists can be requested per request"));
    }
    return Optional.empty();
  }

  // TODO EIP7805 review logic, add counter
  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  protected void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final InclusionListByCommitteeRequestMessage message,
      final ResponseCallback<SignedInclusionList> callback) {

    LOG.trace(
        "Peer {} requested Inclusion Lists for slot {} with committee indices {}",
        peer.getId(),
        message.getSlot(),
        message.getCommitteeIndices().stream()
            .map(SszBit::toString)
            .collect(Collectors.joining(",")));

    final Optional<RequestKey> inclusionListsRequestApproval =
        peer.approveInclusionListsRequest(callback, message.size());

    if (!peer.approveRequest() || inclusionListsRequestApproval.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalInclusionListsRequestedCounter.inc(message.size());

    // TODO EIP7805 review logic / handle errors
    final List<SignedInclusionList> inclusionList =
        inclusionListManager.getInclusionLists(message.getSlot(), message.getCommitteeIndices());
    inclusionList.forEach(callback::respond);
    callback.completeSuccessfully();
  }
}
