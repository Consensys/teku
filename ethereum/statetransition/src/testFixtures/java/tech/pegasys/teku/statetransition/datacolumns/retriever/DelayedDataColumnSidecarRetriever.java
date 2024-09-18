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
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public class DelayedDataColumnSidecarRetriever implements DataColumnSidecarRetriever {
  private final DataColumnSidecarRetriever delegate;
  private final AsyncRunner asyncRunner;
  private Duration delay;

  public DelayedDataColumnSidecarRetriever(
      DataColumnSidecarRetriever delegate, AsyncRunner asyncRunner, Duration delay) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.delay = delay;
  }

  public void setDelay(Duration delay) {
    this.delay = delay;
  }

  private <T> SafeFuture<T> delay(Supplier<SafeFuture<T>> futSupplier) {
    return asyncRunner.getDelayedFuture(delay).thenCompose(__ -> futSupplier.get());
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(DataColumnSlotAndIdentifier columnId) {
    return delay(() -> delegate.retrieve(columnId));
  }
}
