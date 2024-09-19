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
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public class LateInitDataColumnSidecarCustody implements DataColumnSidecarByRootCustody {
  private DataColumnSidecarByRootCustody delegate = null;

  public void init(DataColumnSidecarByRootCustody delegate) {
    if (this.delegate != null) {
      throw new IllegalStateException("Delegate was initialized already");
    }
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecar(
      DataColumnSlotAndIdentifier columnId) {
    if (delegate == null) {
      throw new IllegalStateException("Delegate was not initialized");
    }
    return delegate.getCustodyDataColumnSidecar(columnId);
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getCustodyDataColumnSidecarByRoot(
      DataColumnIdentifier columnId) {
    if (delegate == null) {
      throw new IllegalStateException("Delegate was not initialized");
    }
    return delegate.getCustodyDataColumnSidecarByRoot(columnId);
  }
}
