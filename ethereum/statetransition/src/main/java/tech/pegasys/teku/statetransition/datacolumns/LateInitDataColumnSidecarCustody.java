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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class LateInitDataColumnSidecarCustody implements DataColumnSidecarRecoveringCustody {
  private DataColumnSidecarRecoveringCustody delegate = null;

  public void init(final DataColumnSidecarRecoveringCustody delegate) {
    if (this.delegate != null) {
      throw new IllegalStateException("Delegate was initialized already");
    }
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    checkDelegate();
    return delegate.getCustodyDataColumnSidecar(columnId);
  }

  @Override
  public SafeFuture<Boolean> hasCustodyDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId) {
    return delegate.hasCustodyDataColumnSidecar(columnId);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecarByRoot(
      final DataColumnIdentifier columnId) {
    checkDelegate();
    return delegate.getCustodyDataColumnSidecarByRoot(columnId);
  }

  @Override
  public void onNewBlock(final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    checkDelegate();
    delegate.onNewBlock(block, remoteOrigin);
  }

  @Override
  public void subscribeToValidDataColumnSidecars(
      final DataColumnSidecarManager.ValidDataColumnSidecarsListener sidecarsListener) {
    checkDelegate();
    delegate.subscribeToValidDataColumnSidecars(sidecarsListener);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    checkDelegate();
    delegate.onSlot(slot);
  }

  @Override
  public SafeFuture<Void> onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar) {
    checkDelegate();
    return delegate.onNewValidatedDataColumnSidecar(dataColumnSidecar);
  }

  @Override
  public AsyncStream<DataColumnSlotAndIdentifier> retrieveMissingColumns() {
    checkDelegate();
    return delegate.retrieveMissingColumns();
  }

  private void checkDelegate() {
    if (delegate == null) {
      throw new IllegalStateException("Delegate was not initialized");
    }
  }
}
