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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.util.ColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.eip7594.helpers.MiscHelpersEip7594;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7594;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlobSidecarReconstructionProviderTest {
  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final Spec spec = TestSpecFactory.createMinimalEip7594();
  private final KZG kzg = mock(KZG.class);

  private final BlobSidecarReconstructionProvider blobSidecarReconstructionProvider =
      new BlobSidecarReconstructionProvider(client, spec, kzg);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();
  private static final String VALID_BLOBS_AND_CELLS_FILENAMES = "valid_blobs_and_cells.json";

  @Test
  public void shouldGiveUpIfBlockNotFound() {
    when(client.getBlockAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    final SafeFuture<List<BlobSidecar>> result =
        blobSidecarReconstructionProvider.reconstructBlobSidecars(
            UInt64.ZERO, Collections.emptyList());
    assertThat(result).isCompletedWithValueMatching(List::isEmpty);
    verify(client, never()).getDataColumnIdentifiers(any());
  }

  @Test
  public void shouldGiveUpIfDataColumnIdentifiersNotFound() {
    when(client.getDataColumnIdentifiers(any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    final SafeFuture<List<BlobSidecar>> result =
        blobSidecarReconstructionProvider.reconstructBlobSidecars(
            slotAndBlockRoot, Collections.emptyList());
    assertThat(result).isCompletedWithValueMatching(List::isEmpty);
    verify(client, never()).getSidecar(any(ColumnSlotAndIdentifier.class));
  }

  @Test
  public void shouldGiveUpIfAnyDataColumnIdentifiersMissing() {
    final Integer numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final List<ColumnSlotAndIdentifier> allButLast50Percent =
        IntStream.range(0, numberOfColumns / 2 - 1)
            .mapToObj(
                index ->
                    new ColumnSlotAndIdentifier(
                        slotAndBlockRoot.getSlot(),
                        slotAndBlockRoot.getBlockRoot(),
                        UInt64.valueOf(index)))
            .toList();
    when(client.getDataColumnIdentifiers(any()))
        .thenReturn(SafeFuture.completedFuture(allButLast50Percent));
    final SafeFuture<List<BlobSidecar>> result =
        blobSidecarReconstructionProvider.reconstructBlobSidecars(
            slotAndBlockRoot, Collections.emptyList());
    assertThat(result).isCompletedWithValueMatching(List::isEmpty);
    verify(client, never()).getSidecar(any(ColumnSlotAndIdentifier.class));
  }

  @Test
  public void shouldGiveUpIfAnySidecarsMissing() {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final List<ColumnSlotAndIdentifier> all50Percent =
        IntStream.range(0, numberOfColumns / 2)
            .mapToObj(
                index ->
                    new ColumnSlotAndIdentifier(
                        slotAndBlockRoot.getSlot(),
                        slotAndBlockRoot.getBlockRoot(),
                        UInt64.valueOf(index)))
            .toList();
    final SignedBeaconBlockHeader header =
        dataStructureUtil.randomSignedBeaconBlockHeader(slotAndBlockRoot.getSlot());

    when(client.getDataColumnIdentifiers(any()))
        .thenReturn(SafeFuture.completedFuture(all50Percent));
    when(client.getSidecar(any(ColumnSlotAndIdentifier.class)))
        .thenAnswer(
            invocationOnMock -> {
              final ColumnSlotAndIdentifier identifier = invocationOnMock.getArgument(0);
              if (!identifier.identifier().getIndex().equals(UInt64.valueOf(10))) {
                return SafeFuture.completedFuture(
                    Optional.of(
                        dataStructureUtil.randomDataColumnSidecar(
                            header, identifier.identifier().getIndex())));
              } else {
                return SafeFuture.completedFuture(Optional.empty());
              }
            });

    final SafeFuture<List<BlobSidecar>> result =
        blobSidecarReconstructionProvider.reconstructBlobSidecars(
            slotAndBlockRoot, Collections.emptyList());
    assertThat(result).isCompletedWithValueMatching(List::isEmpty);
    verify(client, times(numberOfColumns / 2)).getSidecar(any(ColumnSlotAndIdentifier.class));
  }

  @Test
  public void shouldGiveUpIfFullBlockMissing() {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final List<ColumnSlotAndIdentifier> all50Percent =
        IntStream.range(0, numberOfColumns / 2)
            .mapToObj(
                index ->
                    new ColumnSlotAndIdentifier(
                        slotAndBlockRoot.getSlot(),
                        slotAndBlockRoot.getBlockRoot(),
                        UInt64.valueOf(index)))
            .toList();
    final SignedBeaconBlockHeader header =
        dataStructureUtil.randomSignedBeaconBlockHeader(slotAndBlockRoot.getSlot());

    when(client.getDataColumnIdentifiers(any()))
        .thenReturn(SafeFuture.completedFuture(all50Percent));
    when(client.getSidecar(any(ColumnSlotAndIdentifier.class)))
        .thenAnswer(
            invocationOnMock -> {
              final ColumnSlotAndIdentifier identifier = invocationOnMock.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.of(
                      dataStructureUtil.randomDataColumnSidecar(
                          header, identifier.identifier().getIndex())));
            });
    when(client.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<List<BlobSidecar>> result =
        blobSidecarReconstructionProvider.reconstructBlobSidecars(
            slotAndBlockRoot, Collections.emptyList());
    assertThat(result).isCompletedWithValueMatching(List::isEmpty);
    verify(client, times(numberOfColumns / 2)).getSidecar(any(ColumnSlotAndIdentifier.class));
  }

  @Test
  public void shouldReturnBlobs() {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final List<ColumnSlotAndIdentifier> all50Percent =
        IntStream.range(0, numberOfColumns / 2)
            .mapToObj(
                index ->
                    new ColumnSlotAndIdentifier(
                        slotAndBlockRoot.getSlot(),
                        slotAndBlockRoot.getBlockRoot(),
                        UInt64.valueOf(index)))
            .toList();
    final BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(2);
    final SignedBeaconBlock block =
        dataStructureUtil.signedBlock(
            dataStructureUtil.randomBeaconBlock(slotAndBlockRoot.getSlot(), beaconBlockBody));

    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    final MiscHelpersEip7594 miscHelpersEip7594 =
        MiscHelpersEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).miscHelpers());

    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersEip7594.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix);

    when(client.getDataColumnIdentifiers(any()))
        .thenReturn(SafeFuture.completedFuture(all50Percent));
    when(client.getSidecar(any(ColumnSlotAndIdentifier.class)))
        .thenAnswer(
            invocationOnMock -> {
              final ColumnSlotAndIdentifier identifier = invocationOnMock.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.of(
                      dataColumnSidecars.get(identifier.identifier().getIndex().intValue())));
            });
    when(client.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(kzg.computeBlobKzgProof(any(), any())).thenReturn(dataStructureUtil.randomKZGProof());

    final SafeFuture<List<BlobSidecar>> result =
        blobSidecarReconstructionProvider.reconstructBlobSidecars(
            slotAndBlockRoot, Collections.emptyList());
    assertThat(result)
        .isCompletedWithValueMatching(
            list ->
                list.size() == 2
                    && list.getFirst().getBlob().equals(blobsAndMatrix.blobs.getFirst())
                    && list.get(1).getBlob().equals(blobsAndMatrix.blobs.get(1)));
    verify(client, times(numberOfColumns / 2)).getSidecar(any(ColumnSlotAndIdentifier.class));
  }

  @Test
  public void shouldReturnBlobsFiltered() {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();
    final List<ColumnSlotAndIdentifier> all50Percent =
        IntStream.range(0, numberOfColumns / 2)
            .mapToObj(
                index ->
                    new ColumnSlotAndIdentifier(
                        slotAndBlockRoot.getSlot(),
                        slotAndBlockRoot.getBlockRoot(),
                        UInt64.valueOf(index)))
            .toList();
    final BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(2);
    final SignedBeaconBlock block =
        dataStructureUtil.signedBlock(
            dataStructureUtil.randomBeaconBlock(slotAndBlockRoot.getSlot(), beaconBlockBody));

    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    final MiscHelpersEip7594 miscHelpersEip7594 =
        MiscHelpersEip7594.required(spec.forMilestone(SpecMilestone.EIP7594).miscHelpers());

    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersEip7594.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix);

    when(client.getDataColumnIdentifiers(any()))
        .thenReturn(SafeFuture.completedFuture(all50Percent));
    when(client.getSidecar(any(ColumnSlotAndIdentifier.class)))
        .thenAnswer(
            invocationOnMock -> {
              final ColumnSlotAndIdentifier identifier = invocationOnMock.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.of(
                      dataColumnSidecars.get(identifier.identifier().getIndex().intValue())));
            });
    when(client.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(kzg.computeBlobKzgProof(any(), any())).thenReturn(dataStructureUtil.randomKZGProof());

    final SafeFuture<List<BlobSidecar>> result =
        blobSidecarReconstructionProvider.reconstructBlobSidecars(
            slotAndBlockRoot, List.of(UInt64.ONE));
    assertThat(result)
        .isCompletedWithValueMatching(
            list ->
                list.size() == 1 && list.getFirst().getBlob().equals(blobsAndMatrix.blobs.get(1)));
    verify(client, times(numberOfColumns / 2)).getSidecar(any(ColumnSlotAndIdentifier.class));
  }

  private BlobsAndMatrix loadBlobsAndMatrixFixture() {
    final List<CellData> cellData = loadJson();
    final SchemaDefinitionsEip7594 schemaDefinitionsEip7594 =
        SchemaDefinitionsEip7594.required(
            spec.forMilestone(SpecMilestone.EIP7594).getSchemaDefinitions());
    final List<Blob> blobs =
        cellData.stream()
            .map(
                blobAndCells ->
                    schemaDefinitionsEip7594
                        .getBlobSchema()
                        .create(Bytes.fromHexString(blobAndCells.blob)))
            .toList();
    final List<List<MatrixEntry>> extendedMatrix = new ArrayList<>();
    for (int blobIndex = 0; blobIndex < cellData.size(); ++blobIndex) {
      final CellData blobCellData = cellData.get(blobIndex);
      final List<MatrixEntry> row = new ArrayList<>();
      for (int cellIndex = 0; cellIndex < blobCellData.cells.size(); ++cellIndex) {
        row.add(
            new MatrixEntry(
                schemaDefinitionsEip7594.getMatrixEntrySchema(),
                schemaDefinitionsEip7594
                    .getCellSchema()
                    .create(Bytes.fromHexString(blobCellData.cells.get(cellIndex))),
                KZGProof.fromBytesCompressed(Bytes48.ZERO),
                UInt64.valueOf(cellIndex),
                UInt64.valueOf(blobIndex)));
      }
      extendedMatrix.add(row);
    }

    return new BlobsAndMatrix(blobs, extendedMatrix);
  }

  private record BlobsAndMatrix(List<Blob> blobs, List<List<MatrixEntry>> extendedMatrix) {}
  ;

  private List<CellData> loadJson() {
    final URL jsonUrl =
        BlobSidecarReconstructionProviderTest.class.getResource(VALID_BLOBS_AND_CELLS_FILENAMES);
    try {
      final String json = Resources.toString(jsonUrl, UTF_8);
      return new ObjectMapper().readValue(json, new TypeReference<>() {});
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static class CellData {
    @JsonProperty(value = "blob", required = true)
    private String blob;

    @JsonProperty(value = "cells", required = true)
    private List<String> cells;
  }
}
