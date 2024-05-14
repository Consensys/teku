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

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint256ToBytes;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.kzg.KZGCellWithColumnId;
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

  private UInt64 computeCustodySubnetIndex(final UInt256 nodeId) {
    return bytesToUInt64(Hash.sha256(uint256ToBytes(nodeId)).slice(0, 8))
        .mod(specConfigEip7594.getDataColumnSidecarSubnetCount());
  }

  public List<UInt64> computeCustodySubnetIndexes(final UInt256 nodeId, final int subnetCount) {
    //     assert custody_subnet_count <= DATA_COLUMN_SIDECAR_SUBNET_COUNT
    if (subnetCount > specConfigEip7594.getDataColumnSidecarSubnetCount()) {
      throw new IllegalArgumentException(
          String.format(
              "Subnet count %s couldn't exceed number of subnet columns %s",
              subnetCount, specConfigEip7594.getNumberOfColumns()));
    }

    return Stream.iterate(nodeId, this::incrementByModule)
        .map(this::computeCustodySubnetIndex)
        .distinct()
        .limit(subnetCount)
        .sorted()
        .toList();
  }

  private UInt256 incrementByModule(UInt256 n) {
    if (n.equals(UInt256.MAX_VALUE)) {
      return UInt256.ZERO;
    } else {
      return n.plus(1);
    }
  }

  public List<UInt64> computeCustodyColumnIndexes(final UInt256 nodeId, final int subnetCount) {
    List<UInt64> subnetIds = computeCustodySubnetIndexes(nodeId, subnetCount);
    final int columnsPerSubnet =
        specConfigEip7594.getNumberOfColumns()
            / specConfigEip7594.getDataColumnSidecarSubnetCount();
    return subnetIds.stream()
        .flatMap(
            subnetId -> IntStream.range(0, columnsPerSubnet).mapToObj(i -> Pair.of(subnetId, i)))
        .map(
            // ColumnIndex(DATA_COLUMN_SIDECAR_SUBNET_COUNT * i + subnet_id)
            pair ->
                specConfigEip7594.getDataColumnSidecarSubnetCount() * pair.getRight()
                    + pair.getLeft().intValue())
        .map(UInt64::valueOf)
        .sorted()
        .toList();
  }

  public List<UInt64> computeDataColumnSidecarBackboneSubnets(
      final UInt256 nodeId, final UInt64 epoch, final int subnetCount) {
    return computeCustodySubnetIndexes(nodeId, subnetCount);
  }

  @Override
  public boolean verifyDataColumnSidecarKzgProof(KZG kzg, DataColumnSidecar dataColumnSidecar) {
    final int dataColumns = specConfigEip7594.getNumberOfColumns();
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
                    KZGCellWithColumnId.fromCellAndColumn(
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

  public int getBlockBodyKzgCommitmentsGeneralizedIndex() {
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
        blobs.stream().parallel().map(blob -> kzg.computeCellsAndProofs(blob.getBytes())).toList();

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
  public boolean isAvailabilityOfBlobSidecarsRequiredAtEpoch(UInt64 currentEpoch, UInt64 epoch) {
    return false;
  }

  @Override
  public Optional<MiscHelpersEip7594> toVersionEip7594() {
    return Optional.of(this);
  }
}
