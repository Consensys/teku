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
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;

public class TestPeer {

  public record Request(
      DataColumnIdentifier dataColumnIdentifier, SafeFuture<DataColumnSidecar> response) {}

  private final AsyncRunner asyncRunner;
  private final UInt256 nodeId;
  private final Duration latency;

  private final Map<DataColumnIdentifier, DataColumnSidecar> availableSidecars = new HashMap<>();
  private final List<Request> requests = new ArrayList<>();
  private int currentRequestLimit = 1000;

  public TestPeer(final AsyncRunner asyncRunner, final UInt256 nodeId, final Duration latency) {
    this.asyncRunner = asyncRunner;
    this.nodeId = nodeId;
    this.latency = latency;
  }

  public void addSidecar(final DataColumnSidecar sidecar) {
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

  public SafeFuture<DataColumnSidecar> requestSidecar(
      final DataColumnIdentifier dataColumnIdentifier) {
    final SafeFuture<DataColumnSidecar> promise = new SafeFuture<>();
    final Request request = new Request(dataColumnIdentifier, promise);
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

  public TestPeer currentRequestLimit(final int currentRequestLimit) {
    this.currentRequestLimit = currentRequestLimit;
    return this;
  }
}
