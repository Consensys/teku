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

package tech.pegasys.teku.statetransition.datacolumns;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public class DasLongPollCustody implements UpdatableDataColumnSidecarCustody {

  private final UpdatableDataColumnSidecarCustody delegate;
  private final AsyncRunner asyncRunner;
  private final Duration longPollRequestTimeout;

  @VisibleForTesting final PendingRequests pendingRequests = new PendingRequests();

  public DasLongPollCustody(
      UpdatableDataColumnSidecarCustody delegate,
      AsyncRunner asyncRunner,
      Duration longPollRequestTimeout) {
    this.delegate = delegate;
    this.asyncRunner = asyncRunner;
    this.longPollRequestTimeout = longPollRequestTimeout;
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    return delegate
        .onNewValidatedDataColumnSidecar(dataColumnSidecar)
        .thenRun(
            () -> {
              final List<SafeFuture<DataColumnSidecar>> pendingRequests =
                  this.pendingRequests.remove(
                      DataColumnIdentifier.createFromSidecar(dataColumnSidecar));
              for (SafeFuture<DataColumnSidecar> pendingRequest : pendingRequests) {
                pendingRequest.complete(dataColumnSidecar);
              }
            });
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      DataColumnIdentifier columnId) {
    SafeFuture<DataColumnSidecar> pendingPromise = addPendingRequest(columnId);
    delegate
        .getCustodyDataColumnSidecar(columnId)
        .finish(
            maybeExistingColumn -> maybeExistingColumn.ifPresent(pendingPromise::complete),
            pendingPromise::completeExceptionally);
    return pendingPromise
        .orTimeout(asyncRunner, longPollRequestTimeout)
        .thenApply(Optional::of)
        .exceptionally(DasLongPollCustody::emptyOnTimeoutElseThrow);
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    return delegate.retrieveMissingColumns();
  }

  private SafeFuture<DataColumnSidecar> addPendingRequest(final DataColumnIdentifier columnId) {
    final SafeFuture<DataColumnSidecar> promise = new SafeFuture<>();
    pendingRequests.add(columnId, promise);
    return promise;
  }

  private static <T> Optional<T> emptyOnTimeoutElseThrow(Throwable err) {
    if (ExceptionUtil.hasCause(err, TimeoutException.class)) {
      return Optional.empty();
    } else {
      throw new CompletionException(err);
    }
  }

  @VisibleForTesting
  static class PendingRequests {
    final Map<DataColumnIdentifier, List<SafeFuture<DataColumnSidecar>>> requests = new HashMap<>();

    synchronized void add(
        final DataColumnIdentifier columnId, final SafeFuture<DataColumnSidecar> promise) {
      clearCancelledPendingRequests();
      requests.computeIfAbsent(columnId, __ -> new ArrayList<>()).add(promise);
    }

    synchronized List<SafeFuture<DataColumnSidecar>> remove(final DataColumnIdentifier columnId) {
      List<SafeFuture<DataColumnSidecar>> ret = requests.remove(columnId);
      return ret == null ? Collections.emptyList() : ret;
    }

    private void clearCancelledPendingRequests() {
      requests.values().forEach(promises -> promises.removeIf(CompletableFuture::isDone));
      requests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
  }
}
