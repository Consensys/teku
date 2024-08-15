/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType.DATA_COLUMN_SIDECAR_SLOT_NOT_IN_RANGE;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType.DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;

public class DataColumnSidecarsByRangeListenerValidatingProxy
    extends AbstractDataColumnSidecarValidator implements RpcResponseListener<DataColumnSidecar> {
  private final RpcResponseListener<DataColumnSidecar> dataColumnSidecarResponseListener;

  private final UInt64 startSlot;
  private final UInt64 endSlot;

  private final Set<UInt64> columns;

  public DataColumnSidecarsByRangeListenerValidatingProxy(
      final Spec spec,
      final Peer peer,
      final RpcResponseListener<DataColumnSidecar> dataColumnSidecarResponseListener,
      final KZG kzg,
      final UInt64 startSlot,
      final UInt64 count,
      final List<UInt64> columns) {
    super(peer, spec, kzg);
    this.dataColumnSidecarResponseListener = dataColumnSidecarResponseListener;
    this.startSlot = startSlot;
    this.endSlot = startSlot.plus(count).minusMinZero(1);
    this.columns = new HashSet<>(columns);
  }

  @Override
  public SafeFuture<?> onResponse(final DataColumnSidecar dataColumnSidecar) {
    return SafeFuture.of(
        () -> {
          final UInt64 dataColumnSidecarSlot = dataColumnSidecar.getSlot();
          if (!dataColumnSidecarSlotIsInRange(dataColumnSidecarSlot)) {
            throw new DataColumnSidecarsResponseInvalidResponseException(
                peer, DATA_COLUMN_SIDECAR_SLOT_NOT_IN_RANGE);
          }

          if (!columns.contains(dataColumnSidecar.getIndex())) {
            throw new DataColumnSidecarsResponseInvalidResponseException(
                peer, DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER);
          }

          verifyKzgProof(dataColumnSidecar);

          return dataColumnSidecarResponseListener.onResponse(dataColumnSidecar);
        });
  }

  private boolean dataColumnSidecarSlotIsInRange(final UInt64 dataColumnSidecarSlot) {
    return dataColumnSidecarSlot.isGreaterThanOrEqualTo(startSlot)
        && dataColumnSidecarSlot.isLessThanOrEqualTo(endSlot);
  }
}
