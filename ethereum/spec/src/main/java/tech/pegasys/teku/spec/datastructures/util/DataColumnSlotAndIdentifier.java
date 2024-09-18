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

package tech.pegasys.teku.spec.datastructures.util;

import java.util.Comparator;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public record DataColumnSlotAndIdentifier(UInt64 slot, DataColumnIdentifier identifier)
    implements Comparable<DataColumnSlotAndIdentifier> {
  public DataColumnSlotAndIdentifier(
      final UInt64 slot, final Bytes32 blockRoot, final UInt64 columnIndex) {
    this(slot, new DataColumnIdentifier(blockRoot, columnIndex));
  }

  public static DataColumnSlotAndIdentifier fromDataColumn(
      final DataColumnSidecar dataColumnSidecar) {
    return new DataColumnSlotAndIdentifier(
        dataColumnSidecar.getSlot(),
        dataColumnSidecar.getBlockRoot(),
        dataColumnSidecar.getIndex());
  }

  @Override
  public int compareTo(@NotNull final DataColumnSlotAndIdentifier o) {
    return Comparator.comparing(DataColumnSlotAndIdentifier::slot)
        .thenComparing(
            columnSlotAndIdentifier -> columnSlotAndIdentifier.identifier().getBlockRoot())
        .thenComparing(columnSlotAndIdentifier -> columnSlotAndIdentifier.identifier().getIndex())
        .compare(this, o);
  }
}
