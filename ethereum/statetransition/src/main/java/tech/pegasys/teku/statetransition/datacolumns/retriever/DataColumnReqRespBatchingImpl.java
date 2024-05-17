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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class DataColumnReqRespBatchingImpl implements DataColumnReqResp {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final BatchDataColumnReqResp batchRpc;

  public DataColumnReqRespBatchingImpl(BatchDataColumnReqResp batchRpc) {
    this.batchRpc = batchRpc;
  }

  private record RequestEntry(
      UInt256 nodeId,
      DataColumnIdentifier columnIdentifier,
      SafeFuture<DataColumnSidecar> promise) {}

  private List<RequestEntry> bufferedRequests = new ArrayList<>();

  @Override
  public synchronized SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      UInt256 nodeId, DataColumnIdentifier columnIdentifier) {
    RequestEntry entry = new RequestEntry(nodeId, columnIdentifier, new SafeFuture<>());
    bufferedRequests.add(entry);
    return entry.promise();
  }

  @Override
  public void flush() {
    final List<RequestEntry> requests;
    synchronized (this) {
      requests = bufferedRequests;
      bufferedRequests = new ArrayList<>();
    }
    Map<UInt256, List<RequestEntry>> byNodes = new HashMap<>();
    for (RequestEntry request : requests) {
      byNodes.computeIfAbsent(request.nodeId, __ -> new ArrayList<>()).add(request);
    }
    for (Map.Entry<UInt256, List<RequestEntry>> entry : byNodes.entrySet()) {
      flushForNode(entry.getKey(), entry.getValue());
    }
  }

  private void flushForNode(UInt256 nodeId, List<RequestEntry> nodeRequests) {
    LOG.info(
        "[nyota] Requesting batch of {} from {}, hash={}",
        nodeRequests.size(),
        "0x..." + nodeId.toHexString().substring(58),
        nodeRequests.hashCode());
    SafeFuture<List<DataColumnSidecar>> response =
        SafeFuture.of(
            () ->
                batchRpc.requestDataColumnSidecar(
                    nodeId, nodeRequests.stream().map(e -> e.columnIdentifier).toList()));

    response.finish(
        resp -> {
          LOG.info(
              "[nyota] Response batch of {} from {}, hash={}",
              resp.size(),
              "0x..." + nodeId.toHexString().substring(58),
              nodeRequests.hashCode());
          Map<DataColumnIdentifier, DataColumnSidecar> byIds = new HashMap<>();
          for (DataColumnSidecar sidecar : resp) {
            byIds.put(DataColumnIdentifier.createFromSidecar(sidecar), sidecar);
          }
          for (RequestEntry nodeRequest : nodeRequests) {
            DataColumnSidecar maybeResponse = byIds.get(nodeRequest.columnIdentifier);
            if (maybeResponse != null) {
              nodeRequest.promise().complete(maybeResponse);
            } else {
              nodeRequest.promise().completeExceptionally(new DasColumnNotAvailableException());
            }
          }
        },
        err ->
            nodeRequests.forEach(
                e -> {
                  LOG.info(
                      "[nyota] Error batch from {}, hash={}, err: {}",
                      nodeId.mod(65536).toHexString(),
                      nodeRequests.hashCode(),
                      e.toString());
                  e.promise().completeExceptionally(err);
                }));
  }

  @Override
  public int getCurrentRequestLimit(UInt256 nodeId) {
    return batchRpc.getCurrentRequestLimit(nodeId);
  }
}
