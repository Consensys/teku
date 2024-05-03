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

package tech.pegasys.teku.spec.logic.versions.eip7594.helpers;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.kzg.KZGCellWithID;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.Cell;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.CellSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumn;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594.BeaconBlockBodyEip7594;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594.BeaconBlockBodySchemaEip7594;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7594;

public class MiscHelpersEip7594 extends MiscHelpersDeneb {

  public static MiscHelpersEip7594 required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionEip7594()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected EIP7594 misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  private final SpecConfigEip7594 specConfigEip7594;
  private final Predicates predicates;
  private final SchemaDefinitionsEip7594 schemaDefinitions;

  public MiscHelpersEip7594(
      final SpecConfigEip7594 specConfig,
      final Predicates predicates,
      final SchemaDefinitionsEip7594 schemaDefinitions) {
    super(specConfig, predicates, schemaDefinitions);
    this.predicates = predicates;
    this.specConfigEip7594 = specConfig;
    this.schemaDefinitions = schemaDefinitions;
  }

  public UInt64 computeSubnetForDataColumnSidecar(UInt64 columnIndex) {
    return columnIndex.mod(specConfigEip7594.getDataColumnSidecarSubnetCount());
  }

  public Set<UInt64> computeCustodyColumnIndexes(
      final UInt256 nodeId, final UInt64 epoch, final int subnetCount) {
    // TODO: implement whatever formula is finalized
    Set<UInt64> subnets =
        new HashSet<>(computeDataColumnSidecarBackboneSubnets(nodeId, epoch, subnetCount));
    return Stream.iterate(UInt64.ZERO, UInt64::increment)
        .limit(specConfigEip7594.getNumberOfColumns().intValue())
        .filter(columnIndex -> subnets.contains(computeSubnetForDataColumnSidecar(columnIndex)))
        .collect(Collectors.toSet());
  }

  public List<UInt64> computeDataColumnSidecarBackboneSubnets(
      final UInt256 nodeId, final UInt64 epoch, final int subnetCount) {
    // TODO: implement whatever formula is finalized
    return IntStream.range(0, subnetCount)
        .mapToObj(index -> computeSubscribedSubnet(nodeId, epoch, index))
        .toList();
  }

  @Override
  public boolean verifyDataColumnSidecarKzgProof(KZG kzg, DataColumnSidecar dataColumnSidecar) {
    final UInt64 dataColumns = specConfigEip7594.getNumberOfColumns();
    if (dataColumnSidecar.getIndex().isGreaterThanOrEqualTo(dataColumns)) {
      return false;
    }

    // Number of rows is the same for cells, commitments, proofs
    if (dataColumnSidecar.getDataColumn().size() != dataColumnSidecar.getSszKZGCommitments().size()
        || dataColumnSidecar.getSszKZGCommitments().size()
            != dataColumnSidecar.getSszKZGProofs().size()) {
      return false;
    }

    return IntStream.range(0, dataColumnSidecar.getSszKZGProofs().size())
        .mapToObj(
            index ->
                kzg.verifyCellProof(
                    dataColumnSidecar.getSszKZGCommitments().get(index).getKZGCommitment(),
                    KZGCellWithID.fromCellAndColumn(
                        new KZGCell(dataColumnSidecar.getDataColumn().get(index).getBytes()),
                        dataColumnSidecar.getIndex().intValue()),
                    dataColumnSidecar.getSszKZGProofs().get(index).getKZGProof()))
        .filter(verificationResult -> !verificationResult)
        .findFirst()
        .orElse(true);
  }

  @Override
  public boolean verifyDataColumnSidecarInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    return predicates.isValidMerkleBranch(
        dataColumnSidecar.getSszKZGCommitments().hashTreeRoot(),
        dataColumnSidecar.getKzgCommitmentsInclusionProof(),
        specConfigEip7594.getKzgCommitmentsInclusionProofDepth().intValue(),
        getBlockBodyKzgCommitmentsGeneralizedIndex(),
        dataColumnSidecar.getBlockBodyRoot());
  }

  private int getBlockBodyKzgCommitmentsGeneralizedIndex() {
    return (int)
        BeaconBlockBodySchemaEip7594.required(schemaDefinitions.getBeaconBlockBodySchema())
            .getBlobKzgCommitmentsGeneralizedIndex();
  }

  public List<Bytes32> computeDataColumnKzgCommitmentsInclusionProof(
      final BeaconBlockBody beaconBlockBody) {
    return MerkleUtil.constructMerkleProof(
        beaconBlockBody.getBackingNode(), getBlockBodyKzgCommitmentsGeneralizedIndex());
  }

  public List<DataColumnSidecar> constructDataColumnSidecars(
      final SignedBeaconBlock signedBeaconBlock, final List<Blob> blobs, final KZG kzg) {
    if (blobs.isEmpty()) {
      return Collections.emptyList();
    }
    final BeaconBlockBodyEip7594 beaconBlockBody =
        BeaconBlockBodyEip7594.required(signedBeaconBlock.getMessage().getBody());
    final SszList<SszKZGCommitment> sszKZGCommitments = beaconBlockBody.getBlobKzgCommitments();
    final List<Bytes32> kzgCommitmentsInclusionProof =
        computeDataColumnKzgCommitmentsInclusionProof(beaconBlockBody);

    final CellSchema cellSchema = schemaDefinitions.getCellSchema();
    final DataColumnSchema dataColumnSchema = schemaDefinitions.getDataColumnSchema();
    final DataColumnSidecarSchema dataColumnSidecarSchema =
        schemaDefinitions.getDataColumnSidecarSchema();
    final SszListSchema<SszKZGProof, ?> kzgProofsSchema =
        dataColumnSidecarSchema.getKzgProofsSchema();

    List<List<KZGCellAndProof>> blobsCellsAndProofs =
        blobs.stream().map(blob -> kzg.computeCellsAndProofs(blob.getBytes())).toList();

    int columnCount = blobsCellsAndProofs.get(0).size();

    return IntStream.range(0, columnCount)
        .mapToObj(
            cellID -> {
              List<KZGCellAndProof> columnData =
                  blobsCellsAndProofs.stream().map(row -> row.get(cellID)).toList();
              List<Cell> columnCells =
                  columnData.stream()
                      .map(KZGCellAndProof::cell)
                      .map(KZGCell::bytes)
                      .map(cellSchema::create)
                      .toList();

              SszList<SszKZGProof> columnProofs =
                  columnData.stream()
                      .map(KZGCellAndProof::proof)
                      .map(SszKZGProof::new)
                      .collect(kzgProofsSchema.collector());
              final DataColumn dataColumn = dataColumnSchema.create(columnCells);

              return dataColumnSidecarSchema.create(
                  UInt64.valueOf(cellID),
                  dataColumn,
                  sszKZGCommitments,
                  columnProofs,
                  signedBeaconBlock.asHeader(),
                  kzgCommitmentsInclusionProof);
            })
        .toList();
  }

  @Override
  public Optional<MiscHelpersEip7594> toVersionEip7594() {
    return Optional.of(this);
  }
}
