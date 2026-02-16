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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;

public abstract class BlobReconstructor {
  final Spec spec;
  final Supplier<BlobSchema> blobSchemaSupplier;

  public BlobReconstructor(final Spec spec, final Supplier<BlobSchema> blobSchemaSupplier) {
    this.spec = spec;
    this.blobSchemaSupplier = blobSchemaSupplier;
  }

  abstract SafeFuture<Optional<List<Blob>>> reconstructBlobs(
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<DataColumnSidecar> existingSidecars,
      final List<UInt64> blobIndices,
      final Function<Bytes32, SafeFuture<Optional<SignedBeaconBlock>>> retrieveSignedBlockByRoot);

  SafeFuture<List<Blob>> reconstructBlobsFromFirstHalfDataColumns(
      final List<DataColumnSidecar> dataColumnSidecars,
      final List<UInt64> blobIndices,
      final BlobSchema blobSchema,
      final Function<Bytes32, SafeFuture<Optional<SignedBeaconBlock>>> retrieveSignedBlockByRoot) {
    if (dataColumnSidecars.isEmpty()) {
      return SafeFuture.completedFuture(Collections.emptyList());
    }
    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(dataColumnSidecars.getFirst().getSlot());
    return dataColumnSidecarUtil
        .getKzgCommitments(dataColumnSidecars.getFirst(), retrieveSignedBlockByRoot)
        .thenApply(
            maybeKzgCommitments -> {
              final int kzgCommitmentsSize =
                  maybeKzgCommitments
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Unable to retrieve kzg commitments to reconstruct blobs from data column sidecars"))
                      .size();
              return IntStream.range(0, kzgCommitmentsSize)
                  .filter(
                      index -> blobIndices.isEmpty() || blobIndices.contains(UInt64.valueOf(index)))
                  .mapToObj(blobIndex -> constructBlob(dataColumnSidecars, blobIndex, blobSchema))
                  .toList();
            });
  }

  Blob constructBlob(
      final List<DataColumnSidecar> dataColumnSidecars,
      final int blobIndex,
      final BlobSchema blobSchema) {
    final Bytes blobBytes =
        dataColumnSidecars.stream()
            .map(dataColumnSidecar -> dataColumnSidecar.getColumn().get(blobIndex).getBytes())
            .reduce(Bytes::concatenate)
            .orElseThrow();
    return blobSchema.create(blobBytes);
  }
}
