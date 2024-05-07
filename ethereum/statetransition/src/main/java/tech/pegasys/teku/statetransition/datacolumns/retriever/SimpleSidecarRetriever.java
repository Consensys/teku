/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.statetransition.datacolumns.ColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarValidator;

// TODO improve thread-safety: external calls are better to do outside of the synchronize block to
// prevent potential dead locks
public class SimpleSidecarRetriever
    implements DataColumnSidecarRetriever, DataColumnPeerManager.PeerListener {

  private final Spec spec;
  private final DataColumnPeerManager peerManager;
  private final DataColumnReqResp reqResp;

  public SimpleSidecarRetriever(
      Spec spec,
      DataColumnPeerManager peerManager,
      DataColumnReqResp reqResp,
      DataColumnSidecarValidator validator) {
    this.spec = spec;
    this.peerManager = peerManager;
    this.reqResp = new ValidatingDataColumnReqResp(peerManager, reqResp, validator);
    peerManager.addPeerListener(this);
  }

  private final Map<ColumnSlotAndIdentifier, RetrieveRequest> pendingRequests =
      new LinkedHashMap<>();
  private final Map<UInt256, ConnectedPeer> connectedPeers = new HashMap<>();

  @Override
  public synchronized SafeFuture<DataColumnSidecar> retrieve(ColumnSlotAndIdentifier columnId) {
    DataColumnPeerSearcher.PeerSearchRequest peerSearchRequest =
        peerManager.requestPeers(columnId.slot(), columnId.identifier().getIndex());

    synchronized (this) {
      RetrieveRequest existingRequest = pendingRequests.get(columnId);
      if (existingRequest == null) {
        RetrieveRequest request = new RetrieveRequest(columnId, peerSearchRequest);
        pendingRequests.put(columnId, request);
        return request.result;
      } else {
        peerSearchRequest.dispose();
        return existingRequest.result;
      }
    }
  }

  private synchronized List<RequestMatch> matchRequestsAndPeers() {
    disposeCancelledRequests();
    return pendingRequests.entrySet().stream()
        .filter(entry -> entry.getValue().activeRpcRequest == null)
        .flatMap(
            entry -> {
              RetrieveRequest request = entry.getValue();
              return findBestMatchingPeer(request).stream()
                  .map(peer -> new RequestMatch(peer, request));
            })
        .toList();
  }

  private Optional<ConnectedPeer> findBestMatchingPeer(RetrieveRequest request) {
    return findMatchingPeers(request).stream()
        .max(Comparator.comparing(peer -> reqResp.getCurrentRequestLimit(peer.nodeId)));
  }

  private Collection<ConnectedPeer> findMatchingPeers(RetrieveRequest request) {
    return connectedPeers.values().stream()
        .filter(peer -> peer.isCustodyFor(request.columnId))
        .filter(peer -> reqResp.getCurrentRequestLimit(peer.nodeId) > 0)
        .toList();
  }

  private void disposeCancelledRequests() {
    Iterator<Map.Entry<ColumnSlotAndIdentifier, RetrieveRequest>> pendingIterator =
        pendingRequests.entrySet().iterator();
    while (pendingIterator.hasNext()) {
      Map.Entry<ColumnSlotAndIdentifier, RetrieveRequest> pendingEntry = pendingIterator.next();
      RetrieveRequest pendingRequest = pendingEntry.getValue();
      if (pendingRequest.result.isCancelled()) {
        pendingIterator.remove();
        pendingRequest.peerSearchRequest.dispose();
        if (pendingRequest.activeRpcRequest != null) {
          pendingRequest.activeRpcRequest.cancel(true);
        }
      }
    }
  }

  // TODO implement triggering of rounds or do it in a finer grained fashion
  void nextRound() {
    List<RequestMatch> matches = matchRequestsAndPeers();
    for (RequestMatch match : matches) {
      SafeFuture<DataColumnSidecar> reqRespPromise =
          reqResp.requestDataColumnSidecar(match.peer.nodeId, match.request.columnId.identifier());
      match.request.activeRpcRequest =
          reqRespPromise.whenComplete(
              (sidecar, err) -> reqRespCompleted(match.request, sidecar, err));
    }

    reqResp.flush();
  }

  @SuppressWarnings("unused")
  private void reqRespCompleted(
      RetrieveRequest request, DataColumnSidecar maybeResult, Throwable maybeError) {
    if (maybeResult != null) {
      synchronized (this) {
        pendingRequests.remove(request.columnId);
      }
      request.peerSearchRequest.dispose();
    } else {
      request.activeRpcRequest = null;
    }
  }

  @Override
  public synchronized void peerConnected(UInt256 nodeId, int extraCustodySubnetCount) {
    connectedPeers.put(nodeId, new ConnectedPeer(nodeId, extraCustodySubnetCount));
  }

  @Override
  public synchronized void peerDisconnected(UInt256 nodeId) {
    connectedPeers.remove(nodeId);
  }

  private static class RetrieveRequest {
    final ColumnSlotAndIdentifier columnId;
    final DataColumnPeerSearcher.PeerSearchRequest peerSearchRequest;
    final SafeFuture<DataColumnSidecar> result = new SafeFuture<>();
    volatile SafeFuture<DataColumnSidecar> activeRpcRequest = null;

    private RetrieveRequest(
        ColumnSlotAndIdentifier columnId,
        DataColumnPeerSearcher.PeerSearchRequest peerSearchRequest) {
      this.columnId = columnId;
      this.peerSearchRequest = peerSearchRequest;
    }
  }

  private class ConnectedPeer {
    final UInt256 nodeId;
    final int extraCustodySubnetCount;

    public ConnectedPeer(UInt256 nodeId, int extraCustodySubnetCount) {
      this.nodeId = nodeId;
      this.extraCustodySubnetCount = extraCustodySubnetCount;
    }

    private Set<UInt64> getNodeCustodyIndexes(UInt64 slot) {
      SpecVersion specVersion = spec.atSlot(slot);
      int minCustodyRequirement =
          SpecConfigEip7594.required(specVersion.getConfig()).getCustodyRequirement();
      return MiscHelpersEip7594.required(specVersion.miscHelpers())
          .computeCustodyColumnIndexes(nodeId, minCustodyRequirement + extraCustodySubnetCount);
    }

    public boolean isCustodyFor(ColumnSlotAndIdentifier columnId) {
      return getNodeCustodyIndexes(columnId.slot()).contains(columnId.identifier().getIndex());
    }
  }

  private record RequestMatch(ConnectedPeer peer, RetrieveRequest request) {}
}
