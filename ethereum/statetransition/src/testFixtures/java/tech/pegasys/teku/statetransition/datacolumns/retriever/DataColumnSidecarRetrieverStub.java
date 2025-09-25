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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public class DataColumnSidecarRetrieverStub implements DataColumnSidecarRetriever {

  public record RetrieveRequest(
      DataColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> future) {}

  public List<RetrieveRequest> requests = new ArrayList<>();
  private final Map<DataColumnSlotAndIdentifier, DataColumnSidecar> readySidecars = new HashMap<>();

  public void addReadyColumnSidecar(final DataColumnSidecar sidecar) {
    final DataColumnSlotAndIdentifier colId = DataColumnSlotAndIdentifier.fromDataColumn(sidecar);
    readySidecars.put(colId, sidecar);
    requests.stream()
        .filter(req -> req.columnId.equals(colId))
        .forEach(req -> req.future.complete(sidecar));
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final RetrieveRequest request = new RetrieveRequest(columnId, new SafeFuture<>());
    requests.add(request);
    final DataColumnSidecar maybeSidecar = readySidecars.get(columnId);
    if (maybeSidecar != null) {
      request.future.complete(maybeSidecar);
    }
    return request.future;
  }

  @Override
  public void flush() {}

  @Override
  public void onNewValidatedSidecar(final DataColumnSidecar sidecar) {}
}
