/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.client;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

public class CryptoBlobReconstructor extends BlobReconstructor {
  private final Supplier<MiscHelpersFulu> miscHelpersFuluSupplier;

  public CryptoBlobReconstructor(final Spec spec, final Supplier<BlobSchema> blobSchemaSupplier) {
    super(spec, blobSchemaSupplier);
    this.miscHelpersFuluSupplier =
        () -> MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  }

  @Override
  public SafeFuture<Optional<List<Blob>>> reconstructBlobs(
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<DataColumnSidecar> existingSidecars,
      final List<UInt64> blobIndices) {
    if (existingSidecars.size() < (spec.getNumberOfDataColumns().orElseThrow() / 2)) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    return SafeFuture.completedFuture(
        Optional.of(reconstructBlobsFromDataColumns(existingSidecars, blobIndices)));
  }

  private List<Blob> reconstructBlobsFromDataColumns(
      final List<DataColumnSidecar> dataColumnSidecars, final List<UInt64> blobIndices) {
    final List<DataColumnSidecar> dataColumnSidecarsAll =
        miscHelpersFuluSupplier.get().reconstructAllDataColumnSidecars(dataColumnSidecars);
    final List<DataColumnSidecar> firstHalfOfDataColumnSidecars =
        dataColumnSidecarsAll.subList(0, spec.getNumberOfDataColumns().orElseThrow() / 2);

    return reconstructBlobsFromFirstHalfDataColumns(
        firstHalfOfDataColumnSidecars, blobIndices, blobSchemaSupplier.get());
  }
}
