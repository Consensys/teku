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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
import tech.pegasys.teku.kzg.KZGCellID;
import tech.pegasys.teku.kzg.KZGCellWithColumnId;
import tech.pegasys.teku.kzg.KZGCellWithIds;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.Cell;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumn;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
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
  private static final MathContext BIGDECIMAL_PRECISION = MathContext.DECIMAL128;

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

    final List<KZGCellWithIds> cellWithIds =
        IntStream.range(0, dataColumnSidecar.getDataColumn().size())
            .mapToObj(
                rowIndex ->
                    KZGCellWithIds.fromCellAndIndices(
                        new KZGCell(dataColumnSidecar.getDataColumn().get(rowIndex).getBytes()),
                        rowIndex,
                        dataColumnSidecar.getIndex().intValue()))
            .collect(Collectors.toList());

    return kzg.verifyCellProofBatch(
        dataColumnSidecar.getSszKZGCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .toList(),
        cellWithIds,
        dataColumnSidecar.getSszKZGProofs().stream().map(SszKZGProof::getKZGProof).toList());
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
    return constructDataColumnSidecars(
        signedBeaconBlock.getMessage(),
        signedBeaconBlock.asHeader(),
        computeExtendedMatrix(blobs, kzg));
  }

  /**
   * Return the full ``ExtendedMatrix``.
   *
   * <p>This helper demonstrates the relationship between blobs and ``ExtendedMatrix``.
   *
   * <p>>The data structure for storing cells is implementation-dependent.
   */
  public List<List<MatrixEntry>> computeExtendedMatrix(final List<Blob> blobs, final KZG kzg) {
    final List<List<MatrixEntry>> extendedMatrix = new ArrayList<>();
    for (int blobIndex = 0; blobIndex < blobs.size(); ++blobIndex) {
      final List<MatrixEntry> row = new ArrayList<>();
      final List<KZGCellAndProof> kzgCellAndProofs =
          kzg.computeCellsAndProofs(blobs.get(blobIndex).getBytes());
      for (int cellIndex = 0; cellIndex < kzgCellAndProofs.size(); ++cellIndex) {
        row.add(
            schemaDefinitions
                .getMatrixEntrySchema()
                .create(
                    kzgCellAndProofs.get(cellIndex).cell(),
                    kzgCellAndProofs.get(cellIndex).proof(),
                    blobIndex,
                    cellIndex));
      }
      extendedMatrix.add(row);
    }
    return extendedMatrix;
  }

  public List<DataColumnSidecar> constructDataColumnSidecars(
      final BeaconBlock beaconBlock,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<List<MatrixEntry>> extendedMatrix) {
    if (extendedMatrix.isEmpty()) {
      return Collections.emptyList();
    }
    final BeaconBlockBodyEip7594 beaconBlockBody =
        BeaconBlockBodyEip7594.required(beaconBlock.getBody());
    final SszList<SszKZGCommitment> sszKZGCommitments = beaconBlockBody.getBlobKzgCommitments();
    final List<Bytes32> kzgCommitmentsInclusionProof =
        computeDataColumnKzgCommitmentsInclusionProof(beaconBlockBody);

    final DataColumnSchema dataColumnSchema = schemaDefinitions.getDataColumnSchema();
    final DataColumnSidecarSchema dataColumnSidecarSchema =
        schemaDefinitions.getDataColumnSidecarSchema();
    final SszListSchema<SszKZGProof, ?> kzgProofsSchema =
        dataColumnSidecarSchema.getKzgProofsSchema();

    int columnCount = extendedMatrix.get(0).size();

    return IntStream.range(0, columnCount)
        .mapToObj(
            cellID -> {
              List<MatrixEntry> columnData =
                  extendedMatrix.stream().map(row -> row.get(cellID)).toList();
              List<Cell> columnCells = columnData.stream().map(MatrixEntry::getCell).toList();

              SszList<SszKZGProof> columnProofs =
                  columnData.stream()
                      .map(MatrixEntry::getKzgProof)
                      .map(SszKZGProof::new)
                      .collect(kzgProofsSchema.collector());
              final DataColumn dataColumn = dataColumnSchema.create(columnCells);

              return dataColumnSidecarSchema.create(
                  UInt64.valueOf(cellID),
                  dataColumn,
                  sszKZGCommitments,
                  columnProofs,
                  signedBeaconBlockHeader,
                  kzgCommitmentsInclusionProof);
            })
        .toList();
  }

  /**
   * Return the recovered extended matrix.
   *
   * <p>This helper demonstrates how to apply ``recover_cells_and_kzg_proofs``.
   *
   * <p>The data structure for storing cells is implementation-dependent.
   */
  public List<List<MatrixEntry>> recoverMatrix(
      final List<List<MatrixEntry>> partialMatrix, final KZG kzg) {
    return IntStream.range(0, partialMatrix.size())
        .parallel()
        .mapToObj(
            blobIndex -> {
              final List<KZGCellWithColumnId> cellWithColumnIds =
                  partialMatrix.get(blobIndex).stream()
                      .filter(entry -> entry.getRowIndex().intValue() == blobIndex)
                      .map(
                          entry ->
                              new KZGCellWithColumnId(
                                  new KZGCell(entry.getCell().getBytes()),
                                  new KZGCellID(entry.getColumnIndex())))
                      .toList();
              final List<KZGCellAndProof> kzgCellAndProofs =
                  kzg.recoverCellsAndProofs(cellWithColumnIds);
              return IntStream.range(0, kzgCellAndProofs.size())
                  .mapToObj(
                      kzgCellAndProofIndex ->
                          schemaDefinitions
                              .getMatrixEntrySchema()
                              .create(
                                  kzgCellAndProofs.get(kzgCellAndProofIndex).cell(),
                                  kzgCellAndProofs.get(kzgCellAndProofIndex).proof(),
                                  kzgCellAndProofIndex,
                                  blobIndex))
                  .toList();
            })
        .toList();
  }

  /**
   * Return the sample count if allowing failures.
   *
   * <p>This helper demonstrates how to calculate the number of columns to query per slot when
   * allowing given number of failures, assuming uniform random selection without replacement.
   * Nested functions are direct replacements of Python library functions math.comb and
   * scipy.stats.hypergeom.cdf, with the same signatures.
   */
  public UInt64 getExtendedSampleCount(final UInt64 allowedFailures) {
    if (allowedFailures.isGreaterThan(specConfigEip7594.getNumberOfColumns() / 2)) {
      throw new IllegalArgumentException(
          String.format(
              "Allowed failures (%s) should be less than half of columns number (%s)",
              allowedFailures, specConfigEip7594.getNumberOfColumns()));
    }
    final UInt64 worstCaseMissing = UInt64.valueOf(specConfigEip7594.getNumberOfColumns() / 2 + 1);
    final double falsePositiveThreshold =
        hypergeomCdf(
            UInt64.ZERO,
            UInt64.valueOf(specConfigEip7594.getNumberOfColumns()),
            worstCaseMissing,
            UInt64.valueOf(specConfigEip7594.getSamplesPerSlot()));
    UInt64 sampleCount = UInt64.valueOf(specConfigEip7594.getSamplesPerSlot());
    for (;
        sampleCount.isLessThanOrEqualTo(specConfigEip7594.getNumberOfColumns());
        sampleCount = sampleCount.increment()) {
      if (hypergeomCdf(
              allowedFailures,
              UInt64.valueOf(specConfigEip7594.getNumberOfColumns()),
              worstCaseMissing,
              sampleCount)
          <= falsePositiveThreshold) {
        break;
      }
    }
    return sampleCount;
  }

  private UInt256 mathComb(final UInt64 n, final UInt64 k) {
    if (n.isGreaterThanOrEqualTo(k)) {
      UInt256 r = UInt256.ONE;
      for (UInt64 i = UInt64.ZERO;
          i.isLessThan(k.isGreaterThan(n.minus(k)) ? n.minus(k) : k);
          i = i.plus(1)) {
        r = r.multiply(n.minus(i).longValue()).divide(i.plus(1).longValue());
      }
      return r;
    } else {
      return UInt256.ZERO;
    }
  }

  @SuppressWarnings("JavaCase")
  private double hypergeomCdf(final UInt64 k, final UInt64 M, final UInt64 n, final UInt64 N) {
    return Stream.iterate(UInt64.ZERO, i -> i.isLessThanOrEqualTo(k), UInt64::increment)
        .mapToDouble(
            i ->
                N.isLessThan(i)
                    ? 0d
                    : new BigDecimal(
                            mathComb(n, i)
                                .multiply(mathComb(M.minus(n), N.minus(i)))
                                .toBigInteger(),
                            BIGDECIMAL_PRECISION)
                        .divide(new BigDecimal(mathComb(M, N).toBigInteger()), BIGDECIMAL_PRECISION)
                        .doubleValue())
        .sum();
  }

  @Override
  public boolean isAvailabilityOfBlobSidecarsRequiredAtEpoch(UInt64 currentEpoch, UInt64 epoch) {
    return false;
  }

  public boolean isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
      final UInt64 currentEpoch, final UInt64 epoch) {
    return currentEpoch
        .minusMinZero(epoch)
        .isLessThanOrEqualTo(specConfigEip7594.getMinEpochsForDataColumnSidecarsRequests());
  }

  @Override
  public Optional<MiscHelpersEip7594> toVersionEip7594() {
    return Optional.of(this);
  }
}
