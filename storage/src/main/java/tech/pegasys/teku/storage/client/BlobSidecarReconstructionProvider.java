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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

/**
 * PeerDAS BlobSidecar provider, when we don't store blobSidecars in DB as is but could reconstruct
 * from DataColumnSidecar's
 */
public class BlobSidecarReconstructionProvider {
  private final CombinedChainDataClient combinedChainDataClient;
  private final Spec spec;
  private final KZG kzg;

  public BlobSidecarReconstructionProvider(
      final CombinedChainDataClient combinedChainDataClient, final Spec spec, final KZG kzg) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.spec = spec;
    this.kzg = kzg;
  }

  public SafeFuture<List<BlobSidecar>> reconstructBlobSidecars(
      final UInt64 slot, final List<UInt64> indices) {
    // TODO: suboptimal
    return combinedChainDataClient
        .getBlockAtSlotExact(slot)
        .thenCompose(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                return SafeFuture.completedFuture(emptyList());
              }
              return reconstructBlobSidecars(maybeBlock.get().getSlotAndBlockRoot(), indices);
            });
  }

  public SafeFuture<List<BlobSidecar>> reconstructBlobSidecars(
      final SlotAndBlockRoot slotAndBlockRoot, final List<UInt64> indices) {
    final SafeFuture<List<DataColumnSlotAndIdentifier>> dataColumnIdentifiersFuture =
        combinedChainDataClient.getDataColumnIdentifiers(slotAndBlockRoot.getSlot());
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
                      // We need first 50% for reconstruction
                      slot -> slot.isLessThan(spec.getNumberOfDataColumns().orElseThrow() / 2),
                      UInt64::increment)
                  .map(
                      index ->
                          new DataColumnSlotAndIdentifier(
                              slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), index))
                  .toList();
          if (requiredIdentifiers.stream()
              .anyMatch(identifier -> !dbIdentifiers.contains(identifier))) {
            return SafeFuture.completedFuture(emptyList());
          }
          return reconstructBlobSidecarsForIdentifiers(requiredIdentifiers, indices);
        });
  }

  private SafeFuture<List<BlobSidecar>> reconstructBlobSidecarsForIdentifiers(
      final List<DataColumnSlotAndIdentifier> slotAndIdentifiers, final List<UInt64> indices) {
    return SafeFuture.collectAll(
            slotAndIdentifiers.stream().map(combinedChainDataClient::getSidecar))
        .thenCompose(
            sidecarOptionals -> {
              if (sidecarOptionals.stream().anyMatch(Optional::isEmpty)) {
                // Will not be able to reconstruct if somehow we got a gap
                return SafeFuture.completedFuture(emptyList());
              }
              final List<DataColumnSidecar> dataColumnSidecars =
                  sidecarOptionals.stream().map(Optional::orElseThrow).toList();

              final SafeFuture<Optional<SignedBeaconBlock>> signedBeaconBlockFuture =
                  combinedChainDataClient.getBlockByBlockRoot(
                      slotAndIdentifiers.getFirst().blockRoot());
              return signedBeaconBlockFuture.thenApply(
                  signedBeaconBlock ->
                      signedBeaconBlock
                          .map(
                              block ->
                                  reconstructBlobSidecarsFromDataColumns(
                                      dataColumnSidecars, block, indices))
                          .orElse(emptyList()));
            });
  }

  private List<BlobSidecar> reconstructBlobSidecarsFromDataColumns(
      final List<DataColumnSidecar> dataColumnSidecars,
      final SignedBeaconBlock signedBeaconBlock,
      final List<UInt64> indices) {
    if (dataColumnSidecars.isEmpty()) {
      return emptyList();
    }

    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers());
    final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
        SchemaDefinitionsDeneb.required(
            spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions());

    return IntStream.range(0, dataColumnSidecars.getFirst().getSszKZGCommitments().size())
        .filter(index -> indices.isEmpty() || indices.contains(UInt64.valueOf(index)))
        .mapToObj(
            blobIndex ->
                constructBlobSidecar(
                    dataColumnSidecars,
                    blobIndex,
                    signedBeaconBlock,
                    miscHelpersDeneb,
                    schemaDefinitionsDeneb))
        .toList();
  }

  private BlobSidecar constructBlobSidecar(
      final List<DataColumnSidecar> dataColumnSidecars,
      final int blobIndex,
      final SignedBeaconBlock signedBeaconBlock,
      final MiscHelpersDeneb miscHelpersDeneb,
      final SchemaDefinitionsDeneb schemaDefinitionsDeneb) {
    final Bytes blobBytes =
        dataColumnSidecars.stream()
            .map(dataColumnSidecar -> dataColumnSidecar.getDataColumn().get(blobIndex).getBytes())
            .reduce(Bytes::concatenate)
            .orElseThrow();
    return miscHelpersDeneb.constructBlobSidecar(
        signedBeaconBlock,
        UInt64.valueOf(blobIndex),
        schemaDefinitionsDeneb.getBlobSchema().create(blobBytes),
        new SszKZGProof(
            kzg.computeBlobKzgProof(
                blobBytes,
                signedBeaconBlock
                    .getMessage()
                    .getBody()
                    .getOptionalBlobKzgCommitments()
                    .orElseThrow()
                    .get(blobIndex)
                    .getKZGCommitment())));
  }
}
