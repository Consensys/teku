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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class TestPeer {

  public record Request(
      DataColumnIdentifier dataColumnIdentifier, SafeFuture<DataColumnSidecar> response) {}

  private final AsyncRunner asyncRunner;
  private final UInt256 nodeId;
  private final Duration latency;

  private final Map<DataColumnIdentifier, DataColumnSidecar> availableSidecars = new HashMap<>();
  private final List<Request> requests = new ArrayList<>();
  private int currentRequestLimit = 0;

  public TestPeer(AsyncRunner asyncRunner, UInt256 nodeId, Duration latency) {
    this.asyncRunner = asyncRunner;
    this.nodeId = nodeId;
    this.latency = latency;
  }

  public void addSidecar(DataColumnSidecar sidecar) {
    availableSidecars.put(DataColumnIdentifier.createFromSidecar(sidecar), sidecar);
  }

  public UInt256 getNodeId() {
    return nodeId;
  }

  public void onDisconnect() {
    requests.stream()
        .filter(r -> !r.response.isDone())
        .forEach(
            r ->
                r.response.completeExceptionally(
                    new DataColumnReqResp.DasPeerDisconnectedException()));
  }

  public SafeFuture<DataColumnSidecar> requestSidecar(DataColumnIdentifier dataColumnIdentifier) {
    SafeFuture<DataColumnSidecar> promise = new SafeFuture<>();
    Request request = new Request(dataColumnIdentifier, promise);
    requests.add(request);
    asyncRunner
        .runAfterDelay(
            () -> {
              if (!promise.isDone()) {
                DataColumnSidecar maybeSidecar = availableSidecars.get(dataColumnIdentifier);
                if (maybeSidecar != null) {
                  promise.complete(maybeSidecar);
                } else {
                  promise.completeExceptionally(
                      new DataColumnReqResp.DasColumnNotAvailableException());
                }
              }
            },
            latency)
        .ifExceptionGetsHereRaiseABug();
    return promise;
  }

  public List<Request> getRequests() {
    return requests;
  }

  public int getCurrentRequestLimit() {
    return currentRequestLimit;
  }

  public void setCurrentRequestLimit(int currentRequestLimit) {
    this.currentRequestLimit = currentRequestLimit;
  }
}
