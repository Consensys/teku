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

package tech.pegasys.teku.storage.client;

import static java.util.Collections.emptyList;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

/** Blob provider for post-FULU, reconstructs Blobs from DataColumnSidecars */
public class BlobReconstructionProvider {
  private final CombinedChainDataClient combinedChainDataClient;
  private final Spec spec;
  private final Supplier<BlobSchema> blobSchemaSupplier;

  public BlobReconstructionProvider(
      final CombinedChainDataClient combinedChainDataClient, final Spec spec) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.spec = spec;
    this.blobSchemaSupplier =
        () ->
            SchemaDefinitionsDeneb.required(
                    spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions())
                .getBlobSchema();
  }

  public SafeFuture<List<Blob>> reconstructBlobs(
      final SlotAndBlockRoot slotAndBlockRoot,
      final List<UInt64> blobIndices,
      final boolean isCanonical) {
    final SafeFuture<List<DataColumnSlotAndIdentifier>> dataColumnIdentifiersFuture =
        isCanonical
            ? combinedChainDataClient.getDataColumnIdentifiers(slotAndBlockRoot.getSlot())
            : combinedChainDataClient.getNonCanonicalDataColumnIdentifiers(
                slotAndBlockRoot.getSlot());
    return dataColumnIdentifiersFuture.thenCompose(
        dataColumnIdentifiers -> {
          if (dataColumnIdentifiers.isEmpty()) {
            return SafeFuture.completedFuture(emptyList());
          }
          final Set<DataColumnSlotAndIdentifier> dbIdentifiers =
              new HashSet<>(dataColumnIdentifiers);
          final List<DataColumnSlotAndIdentifier> requiredIdentifiers =
              Stream.iterate(
                      UInt64.ZERO,
                      // We need the first 50% for reconstruction
                      index -> index.isLessThan(spec.getNumberOfDataColumns().orElseThrow() / 2),
                      UInt64::increment)
                  .map(
                      index ->
                          new DataColumnSlotAndIdentifier(
                              slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), index))
                  .toList();
          if (requiredIdentifiers.stream()
              .anyMatch(identifier -> !dbIdentifiers.contains(identifier))) {
            // We do not have the data columns required for reconstruction
            return SafeFuture.completedFuture(emptyList());
          }
          return reconstructBlobSidecarsForIdentifiers(
              requiredIdentifiers, blobIndices, isCanonical);
        });
  }

  private SafeFuture<List<Blob>> reconstructBlobSidecarsForIdentifiers(
      final List<DataColumnSlotAndIdentifier> slotAndIdentifiers,
      final List<UInt64> blobIndices,
      final boolean isCanonical) {
    return SafeFuture.collectAll(
            slotAndIdentifiers.stream()
                .map(
                    identifier ->
                        isCanonical
                            ? combinedChainDataClient.getSidecar(identifier)
                            : combinedChainDataClient.getNonCanonicalSidecar(identifier)))
        .thenApply(
            sidecarOptionals -> {
              if (sidecarOptionals.stream().anyMatch(Optional::isEmpty)) {
                // We will not be able to reconstruct if there is a gap
                return emptyList();
              }
              final List<DataColumnSidecar> dataColumnSidecars =
                  sidecarOptionals.stream().map(Optional::orElseThrow).toList();

              return reconstructBlobsFromDataColumns(dataColumnSidecars, blobIndices);
            });
  }

  private List<Blob> reconstructBlobsFromDataColumns(
      final List<DataColumnSidecar> dataColumnSidecars, final List<UInt64> blobIndices) {
    if (dataColumnSidecars.isEmpty()) {
      return emptyList();
    }

    return IntStream.range(0, dataColumnSidecars.getFirst().getKzgCommitments().size())
        .filter(index -> blobIndices.isEmpty() || blobIndices.contains(UInt64.valueOf(index)))
        .mapToObj(blobIndex -> constructBlob(dataColumnSidecars, blobIndex))
        .toList();
  }

  private Blob constructBlob(
      final List<DataColumnSidecar> dataColumnSidecars, final int blobIndex) {
    final Bytes blobBytes =
        dataColumnSidecars.stream()
            .map(dataColumnSidecar -> dataColumnSidecar.getColumn().get(blobIndex).getBytes())
            .reduce(Bytes::concatenate)
            .orElseThrow();
    return blobSchemaSupplier.get().create(blobBytes);
  }
}
