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

package tech.pegasys.teku.storage.server;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.api.SidecarQueryChannel;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;

/**
 * Splits sidecar storage operations into separate {@link SidecarUpdateChannel} and {@link
 * SidecarQueryChannel} components with updates being handled synchronously and queries being run
 * asynchronously.
 *
 * <p>This guarantees that queries are only ever processed after the updates that were sent before
 * them but without allowing queries to delay updates.
 *
 * <p>This splitter is isolated from {@link CombinedStorageChannelSplitter} to ensure data column
 * operations don't interfere with block/state operations under high load.
 */
public class SidecarStorageChannelSplitter implements SidecarUpdateChannel, SidecarQueryChannel {

  private final AsyncRunner asyncRunner;
  private final SidecarUpdateChannel updateDelegate;
  private final SidecarQueryChannel queryDelegate;

  public SidecarStorageChannelSplitter(
      final AsyncRunner asyncRunner,
      final SidecarUpdateChannel updateDelegate,
      final SidecarQueryChannel queryDelegate) {
    this.asyncRunner = asyncRunner;
    this.updateDelegate = updateDelegate;
    this.queryDelegate = queryDelegate;
  }

  // === Update methods (synchronous) ===

  @Override
  public SafeFuture<Void> onFirstCustodyIncompleteSlot(final UInt64 slot) {
    return updateDelegate.onFirstCustodyIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Void> onEarliestAvailableDataColumnSlot(final UInt64 slot) {
    return updateDelegate.onEarliestAvailableDataColumnSlot(slot);
  }

  @Override
  public SafeFuture<Void> onNewSidecar(final DataColumnSidecar sidecar) {
    return updateDelegate.onNewSidecar(sidecar);
  }

  @Override
  public SafeFuture<Void> onNewNonCanonicalSidecar(final DataColumnSidecar sidecar) {
    return updateDelegate.onNewNonCanonicalSidecar(sidecar);
  }

  // === Query methods (asynchronous via asyncRunner) ===

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return asyncRunner.runAsync(queryDelegate::getFirstCustodyIncompleteSlot);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestAvailableDataColumnSlot() {
    return asyncRunner.runAsync(queryDelegate::getEarliestAvailableDataColumnSlot);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return asyncRunner.runAsync(() -> queryDelegate.getSidecar(identifier));
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getNonCanonicalSidecar(
      final DataColumnSlotAndIdentifier identifier) {
    return asyncRunner.runAsync(() -> queryDelegate.getNonCanonicalSidecar(identifier));
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(final UInt64 slot) {
    return asyncRunner.runAsync(() -> queryDelegate.getDataColumnIdentifiers(slot));
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getNonCanonicalDataColumnIdentifiers(
      final UInt64 slot) {
    return asyncRunner.runAsync(() -> queryDelegate.getNonCanonicalDataColumnIdentifiers(slot));
  }

  @Override
  public SafeFuture<List<DataColumnSlotAndIdentifier>> getDataColumnIdentifiers(
      final UInt64 startSlot, final UInt64 endSlot, final UInt64 limit) {
    return asyncRunner.runAsync(
        () -> queryDelegate.getDataColumnIdentifiers(startSlot, endSlot, limit));
  }

  @Override
  public SafeFuture<Optional<UInt64>> getEarliestDataColumnSidecarSlot() {
    return asyncRunner.runAsync(queryDelegate::getEarliestDataColumnSidecarSlot);
  }

  @Override
  public SafeFuture<Optional<List<List<KZGProof>>>> getDataColumnSidecarsProofs(final UInt64 slot) {
    return asyncRunner.runAsync(() -> queryDelegate.getDataColumnSidecarsProofs(slot));
  }
}
