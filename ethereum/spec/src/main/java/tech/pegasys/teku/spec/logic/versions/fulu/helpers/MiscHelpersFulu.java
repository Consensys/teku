/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint256ToBytes;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Bytes;
import it.unimi.dsi.fastutil.ints.IntList;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
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
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.Cell;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BlindedBeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodySchemaElectra;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class MiscHelpersFulu extends MiscHelpersElectra {
  private static final MathContext BIGDECIMAL_PRECISION = MathContext.DECIMAL128;

  public static MiscHelpersFulu required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionFulu()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Fulu misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  @SuppressWarnings("unused")
  private final SpecConfigFulu specConfigFulu;

  @SuppressWarnings("unused")
  private final Predicates predicates;

  @SuppressWarnings("unused")
  private final SchemaDefinitionsFulu schemaDefinitions;

  private final List<BlobScheduleEntry> blobSchedule;

  public MiscHelpersFulu(
      final SpecConfigFulu specConfigFulu,
      final Predicates predicates,
      final SchemaDefinitions schemaDefinitions) {
    super(
        SpecConfigElectra.required(specConfigFulu),
        predicates,
        SchemaDefinitionsElectra.required(schemaDefinitions));
    this.predicates = predicates;
    this.specConfigFulu = specConfigFulu;
    this.schemaDefinitions = SchemaDefinitionsFulu.required(schemaDefinitions);
    this.blobSchedule =
        specConfigFulu.getBlobSchedule().stream()
            .sorted(Comparator.comparing(BlobScheduleEntry::epoch))
            .toList();
  }

  @Override
  public Optional<MiscHelpersFulu> toVersionFulu() {
    return Optional.of(this);
  }

  // compute_fork_version
  public Bytes4 computeForkVersion(final UInt64 epoch) {
    if (epoch.isGreaterThanOrEqualTo(specConfigFulu.getFuluForkEpoch())) {
      return specConfigFulu.getFuluForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfigFulu.getElectraForkEpoch())) {
      return specConfigFulu.getElectraForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfigFulu.getDenebForkEpoch())) {
      return specConfigFulu.getDenebForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfigFulu.getCapellaForkEpoch())) {
      return specConfigFulu.getCapellaForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfigFulu.getBellatrixForkEpoch())) {
      return specConfigFulu.getBellatrixForkVersion();
    } else if (epoch.isGreaterThanOrEqualTo(specConfigFulu.getAltairForkEpoch())) {
      return specConfigFulu.getAltairForkVersion();
    }
    return specConfigFulu.getGenesisForkVersion();
  }

  // compute_fork_digest
  public Bytes4 computeForkDigest(final Bytes32 genesisValidatorsRoot, final UInt64 epoch) {
    final Bytes4 forkVersion = computeForkVersion(epoch);
    final BlobParameters blobParameters = getBlobParameters(epoch);
    final Bytes32 baseDigest = computeForkDataRoot(forkVersion, genesisValidatorsRoot);
    // Bitmask digest with hash of blob parameters
    return new Bytes4(baseDigest.xor(blobParameters.hash()).slice(0, 4));
  }

  public Optional<Integer> getHighestMaxBlobsPerBlockFromSchedule() {
    return blobSchedule.stream()
        .max(Comparator.comparing(BlobScheduleEntry::maxBlobsPerBlock))
        .map(BlobScheduleEntry::maxBlobsPerBlock);
  }

  // get_blob_parameters
  public BlobParameters getBlobParameters(final UInt64 epoch) {
    return blobSchedule.stream()
        .sorted(Comparator.comparing(BlobScheduleEntry::epoch).reversed())
        .filter(entry -> epoch.isGreaterThanOrEqualTo(entry.epoch()))
        .findFirst()
        .map(BlobParameters::fromBlobScheduleEntry)
        .orElse(
            new BlobParameters(
                specConfigFulu.getElectraForkEpoch(), specConfigFulu.getMaxBlobsPerBlock()));
  }

  private UInt256 incrementByModule(final UInt256 n) {
    if (n.equals(UInt256.MAX_VALUE)) {
      return UInt256.ZERO;
    } else {
      return n.plus(1);
    }
  }

  public UInt64 computeSubnetForDataColumnSidecar(final UInt64 columnIndex) {
    return columnIndex.mod(specConfigFulu.getDataColumnSidecarSubnetCount());
  }

  public List<UInt64> computeDataColumnSidecarBackboneSubnets(
      final UInt256 nodeId, final UInt64 epoch, final int groupCount) {
    final List<UInt64> columns = computeCustodyColumnIndexes(nodeId, groupCount);
    return columns.stream().map(this::computeSubnetForDataColumnSidecar).toList();
  }

  public List<UInt64> computeCustodyColumnIndexes(final UInt256 nodeId, final int groupCount) {
    final List<UInt64> custodyGroups = getCustodyGroups(nodeId, groupCount);
    return custodyGroups.stream()
        .flatMap(group -> computeColumnsForCustodyGroup(group).stream())
        .toList();
  }

  public List<UInt64> computeColumnsForCustodyGroup(final UInt64 custodyGroup) {
    if (custodyGroup.isGreaterThanOrEqualTo(specConfigFulu.getNumberOfCustodyGroups())) {
      throw new IllegalArgumentException(
          String.format(
              "Custody group %s couldn't exceed number of groups %s",
              custodyGroup, specConfigFulu.getNumberOfCustodyGroups()));
    }

    final int columnsPerGroup =
        specConfigFulu.getNumberOfColumns() / specConfigFulu.getNumberOfCustodyGroups();

    return IntStream.range(0, columnsPerGroup)
        .mapToLong(
            i -> (long) specConfigFulu.getNumberOfCustodyGroups() * i + custodyGroup.intValue())
        .sorted()
        .mapToObj(UInt64::valueOf)
        .toList();
  }

  private UInt64 computeCustodyGroupIndex(final UInt256 nodeId) {
    return bytesToUInt64(Hash.sha256(uint256ToBytes(nodeId)).slice(0, 8))
        .mod(specConfigFulu.getNumberOfCustodyGroups());
  }

  public List<UInt64> getCustodyGroups(final UInt256 nodeId, final int custodyGroupCount) {
    if (custodyGroupCount > specConfigFulu.getNumberOfCustodyGroups()) {
      throw new IllegalArgumentException(
          String.format(
              "Custody group count %s couldn't exceed number of groups %s",
              custodyGroupCount, specConfigFulu.getNumberOfCustodyGroups()));
    }

    return Stream.iterate(nodeId, this::incrementByModule)
        .map(this::computeCustodyGroupIndex)
        .distinct()
        .limit(custodyGroupCount)
        .sorted()
        .toList();
  }

  public UInt64 getValidatorsCustodyRequirement(
      final BeaconState state, final Set<UInt64> validatorIndices) {
    final UInt64 totalNodeBalance =
        validatorIndices.stream()
            .map(
                proposerIndex -> {
                  final Validator validator = state.getValidators().get(proposerIndex.intValue());
                  return validator.getEffectiveBalance();
                })
            .reduce(UInt64.ZERO, UInt64::plus);
    final UInt64 count =
        totalNodeBalance.dividedBy(specConfigFulu.getBalancePerAdditionalCustodyGroup());
    return count
        .max(specConfigFulu.getValidatorCustodyRequirement())
        .min(specConfigFulu.getNumberOfCustodyGroups());
  }

  public boolean verifyDataColumnSidecarKzgProof(
      final KZG kzg, final DataColumnSidecar dataColumnSidecar) {
    final int dataColumns = specConfigFulu.getNumberOfColumns();
    if (dataColumnSidecar.getIndex().isGreaterThanOrEqualTo(dataColumns)) {
      return false;
    }

    // Number of rows is the same for cells, commitments, proofs
    if (dataColumnSidecar.getDataColumn().size() != dataColumnSidecar.getSszKZGCommitments().size()
        || dataColumnSidecar.getSszKZGCommitments().size()
            != dataColumnSidecar.getSszKZGProofs().size()) {
      return false;
    }

    final List<KZGCellWithColumnId> cellWithIds =
        IntStream.range(0, dataColumnSidecar.getDataColumn().size())
            .mapToObj(
                rowIndex ->
                    KZGCellWithColumnId.fromCellAndColumn(
                        new KZGCell(dataColumnSidecar.getDataColumn().get(rowIndex).getBytes()),
                        dataColumnSidecar.getIndex().intValue()))
            .collect(Collectors.toList());

    return kzg.verifyCellProofBatch(
        dataColumnSidecar.getSszKZGCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .toList(),
        cellWithIds,
        dataColumnSidecar.getSszKZGProofs().stream().map(SszKZGProof::getKZGProof).toList());
  }

  public boolean verifyDataColumnSidecarInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    if (dataColumnSidecar.getSszKZGCommitments().isEmpty()) {
      return false;
    }
    return predicates.isValidMerkleBranch(
        dataColumnSidecar.getSszKZGCommitments().hashTreeRoot(),
        dataColumnSidecar.getKzgCommitmentsInclusionProof(),
        specConfigFulu.getKzgCommitmentsInclusionProofDepth().intValue(),
        getBlockBodyKzgCommitmentsGeneralizedIndex(),
        dataColumnSidecar.getBlockBodyRoot());
  }

  public int getBlockBodyKzgCommitmentsGeneralizedIndex() {
    return (int)
        BeaconBlockBodySchemaElectra.required(schemaDefinitions.getBeaconBlockBodySchema())
            .getBlobKzgCommitmentsGeneralizedIndex();
  }

  public List<Bytes32> computeDataColumnKzgCommitmentsInclusionProof(
      final BeaconBlockBody beaconBlockBody) {
    return MerkleUtil.constructMerkleProof(
        beaconBlockBody.getBackingNode(), getBlockBodyKzgCommitmentsGeneralizedIndex());
  }

  @VisibleForTesting
  @Deprecated
  public List<DataColumnSidecar> constructDataColumnSidecarsOld(
      final SignedBeaconBlock signedBeaconBlock, final List<Blob> blobs, final KZG kzg) {
    return constructDataColumnSidecars(
        signedBeaconBlock.getMessage(),
        signedBeaconBlock.asHeader(),
        computeExtendedMatrixAndProofs(blobs, kzg));
  }

  public List<DataColumnSidecar> constructDataColumnSidecars(
      final SignedBeaconBlock signedBeaconBlock,
      final List<BlobAndCellProofs> blobAndCellProofsList,
      final KZG kzg) {
    return constructDataColumnSidecars(
        signedBeaconBlock.getMessage(),
        signedBeaconBlock.asHeader(),
        computeExtendedMatrix(blobAndCellProofsList, kzg));
  }

  public List<DataColumnSidecar> constructDataColumnSidecars(
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final SszList<SszKZGCommitment> sszKZGCommitments,
      final List<Bytes32> kzgCommitmentsInclusionProof,
      final List<BlobAndCellProofs> blobAndCellProofsList,
      final KZG kzg) {
    final List<List<MatrixEntry>> extendedMatrix =
        computeExtendedMatrix(blobAndCellProofsList, kzg);
    return constructDataColumnSidecars(
        signedBeaconBlockHeader, sszKZGCommitments, kzgCommitmentsInclusionProof, extendedMatrix);
  }

  /**
   * Return the full ``ExtendedMatrix``.
   *
   * <p>This helper demonstrates the relationship between blobs and ``ExtendedMatrix``.
   *
   * <p>>The data structure for storing cells is implementation-dependent.
   */
  public List<List<MatrixEntry>> computeExtendedMatrixAndProofs(
      final List<Blob> blobs, final KZG kzg) {
    return IntStream.range(0, blobs.size())
        .parallel()
        .mapToObj(
            blobIndex -> {
              final List<KZGCellAndProof> kzgCellAndProofs =
                  kzg.computeCellsAndProofs(blobs.get(blobIndex).getBytes());
              final List<MatrixEntry> row = new ArrayList<>();
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
              return row;
            })
        .toList();
  }

  public List<List<MatrixEntry>> computeExtendedMatrix(
      final List<BlobAndCellProofs> blobAndCellProofsList, final KZG kzg) {
    return IntStream.range(0, blobAndCellProofsList.size())
        .parallel()
        .mapToObj(
            blobIndex -> {
              final BlobAndCellProofs blobAndCellProofs = blobAndCellProofsList.get(blobIndex);
              final List<KZGCell> kzgCells = kzg.computeCells(blobAndCellProofs.blob().getBytes());
              final List<MatrixEntry> row = new ArrayList<>();
              for (int cellIndex = 0; cellIndex < kzgCells.size(); ++cellIndex) {
                row.add(
                    schemaDefinitions
                        .getMatrixEntrySchema()
                        .create(
                            kzgCells.get(cellIndex),
                            blobAndCellProofs.cellProofs().get(cellIndex),
                            blobIndex,
                            cellIndex));
              }
              return row;
            })
        .toList();
  }

  @VisibleForTesting
  public List<DataColumnSidecar> constructDataColumnSidecars(
      final BeaconBlock beaconBlock,
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final List<List<MatrixEntry>> extendedMatrix) {
    if (extendedMatrix.isEmpty()) {
      return Collections.emptyList();
    }

    final SszList<SszKZGCommitment> sszKZGCommitments;
    final List<Bytes32> kzgCommitmentsInclusionProof;
    if (beaconBlock.isBlinded()) {
      final BlindedBeaconBlockBodyDeneb beaconBlockBody =
          BlindedBeaconBlockBodyDeneb.required(beaconBlock.getBody());
      sszKZGCommitments = beaconBlockBody.getBlobKzgCommitments();
      kzgCommitmentsInclusionProof = computeDataColumnKzgCommitmentsInclusionProof(beaconBlockBody);
    } else {
      final BeaconBlockBodyDeneb beaconBlockBody =
          BeaconBlockBodyDeneb.required(beaconBlock.getBody());
      sszKZGCommitments = beaconBlockBody.getBlobKzgCommitments();
      kzgCommitmentsInclusionProof = computeDataColumnKzgCommitmentsInclusionProof(beaconBlockBody);
    }

    return constructDataColumnSidecars(
        signedBeaconBlockHeader, sszKZGCommitments, kzgCommitmentsInclusionProof, extendedMatrix);
  }

  private List<DataColumnSidecar> constructDataColumnSidecars(
      final SignedBeaconBlockHeader signedBeaconBlockHeader,
      final SszList<SszKZGCommitment> sszKZGCommitments,
      final List<Bytes32> kzgCommitmentsInclusionProof,
      final List<List<MatrixEntry>> extendedMatrix) {
    if (extendedMatrix.isEmpty()) {
      return Collections.emptyList();
    }

    final DataColumnSchema dataColumnSchema = schemaDefinitions.getDataColumnSchema();
    final DataColumnSidecarSchema dataColumnSidecarSchema =
        schemaDefinitions.getDataColumnSidecarSchema();
    final SszListSchema<SszKZGProof, ?> kzgProofsSchema =
        dataColumnSidecarSchema.getKzgProofsSchema();

    final int columnCount = extendedMatrix.getFirst().size();

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

  public List<DataColumnSidecar> reconstructAllDataColumnSidecars(
      final Collection<DataColumnSidecar> existingSidecars, final KZG kzg) {
    if (existingSidecars.size() < (specConfigFulu.getNumberOfColumns() / 2)) {
      throw new IllegalArgumentException(
          "Number of sidecars must be greater than or equal to the half of column count");
    }
    final List<List<MatrixEntry>> columnBlobEntries =
        existingSidecars.stream()
            .map(
                sideCar ->
                    IntStream.range(0, sideCar.getDataColumn().size())
                        .mapToObj(
                            rowIndex ->
                                schemaDefinitions
                                    .getMatrixEntrySchema()
                                    .create(
                                        sideCar.getDataColumn().get(rowIndex),
                                        sideCar.getSszKZGProofs().get(rowIndex).getKZGProof(),
                                        sideCar.getIndex(),
                                        UInt64.valueOf(rowIndex)))
                        .toList())
            .toList();
    final List<List<MatrixEntry>> blobColumnEntries = transpose(columnBlobEntries);
    final List<List<MatrixEntry>> extendedMatrix = recoverMatrix(blobColumnEntries, kzg);
    final DataColumnSidecar anyExistingSidecar =
        existingSidecars.stream().findFirst().orElseThrow();
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        anyExistingSidecar.getSignedBeaconBlockHeader();
    return constructDataColumnSidecars(
        signedBeaconBlockHeader,
        anyExistingSidecar.getSszKZGCommitments(),
        anyExistingSidecar.getKzgCommitmentsInclusionProof().asListUnboxed(),
        extendedMatrix);
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
   * NOTE: this method was part of the spec for lossy sampling. Not it is only being used on tests
   * (eventually it will be removed).
   *
   * <p>Return the sample count if allowing failures.
   *
   * <p>This helper demonstrates how to calculate the number of columns to query per slot when
   * allowing given number of failures, assuming uniform random selection without replacement.
   * Nested functions are direct replacements of Python library functions math.comb and
   * scipy.stats.hypergeom.cdf, with the same signatures.
   */
  public UInt64 getExtendedSampleCount(final UInt64 allowedFailures) {
    if (allowedFailures.isGreaterThan(specConfigFulu.getNumberOfColumns() / 2)) {
      throw new IllegalArgumentException(
          String.format(
              "Allowed failures (%s) should be less than half of columns number (%s)",
              allowedFailures, specConfigFulu.getNumberOfColumns()));
    }
    final UInt64 worstCaseMissing = UInt64.valueOf(specConfigFulu.getNumberOfColumns() / 2 + 1);
    final double falsePositiveThreshold =
        hypergeomCdf(
            UInt64.ZERO,
            UInt64.valueOf(specConfigFulu.getNumberOfColumns()),
            worstCaseMissing,
            UInt64.valueOf(specConfigFulu.getSamplesPerSlot()));
    UInt64 sampleCount = UInt64.valueOf(specConfigFulu.getSamplesPerSlot());
    for (;
        sampleCount.isLessThanOrEqualTo(specConfigFulu.getNumberOfColumns());
        sampleCount = sampleCount.increment()) {
      if (hypergeomCdf(
              allowedFailures,
              UInt64.valueOf(specConfigFulu.getNumberOfColumns()),
              worstCaseMissing,
              sampleCount)
          <= falsePositiveThreshold) {
        break;
      }
    }
    return sampleCount;
  }

  private static <T> List<List<T>> transpose(final List<List<T>> matrix) {
    final int rowCount = matrix.size();
    final int colCount = matrix.getFirst().size();
    final List<List<T>> ret =
        Stream.generate(() -> (List<T>) new ArrayList<T>(rowCount)).limit(colCount).toList();

    for (int row = 0; row < rowCount; row++) {
      if (matrix.get(row).size() != colCount) {
        throw new IllegalArgumentException("Different number columns in the matrix");
      }
      for (int col = 0; col < colCount; col++) {
        final T val = matrix.get(row).get(col);
        ret.get(col).add(row, val);
      }
    }
    return ret;
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
  public boolean isAvailabilityOfBlobSidecarsRequiredAtEpoch(
      final UInt64 currentEpoch, final UInt64 epoch) {
    return !epoch.isGreaterThanOrEqualTo(specConfigFulu.getFuluForkEpoch());
  }

  public boolean isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
      final UInt64 currentEpoch, final UInt64 epoch) {
    return currentEpoch
        .minusMinZero(epoch)
        .isLessThanOrEqualTo(specConfigFulu.getMinEpochsForDataColumnSidecarsRequests());
  }

  // compute_proposer_indices
  @Override
  public List<Integer> computeProposerIndices(
      final BeaconState state,
      final UInt64 epoch,
      final Bytes32 epochSeed,
      final IntList activeValidatorIndices) {
    final UInt64 startSlot = computeStartSlotAtEpoch(epoch);
    return IntStream.range(0, specConfigFulu.getSlotsPerEpoch())
        .mapToObj(
            i -> {
              final Bytes32 seed =
                  Hash.sha256(
                      Bytes.concat(
                          epochSeed.toArray(), uint64ToBytes(startSlot.plus(i)).toArray()));
              return computeProposerIndex(state, activeValidatorIndices, seed);
            })
        .toList();
  }

  // initialize_proposer_lookahead
  public List<UInt64> initializeProposerLookahead(
      final BeaconStateElectra state, final BeaconStateAccessorsFulu beaconAccessors) {
    final UInt64 currentEpoch = computeEpochAtSlot(state.getSlot());
    return IntStream.rangeClosed(0, specConfigFulu.getMinSeedLookahead())
        .flatMap(
            i ->
                beaconAccessors.getBeaconProposerIndices(state, currentEpoch.plus(i)).stream()
                    .mapToInt(Integer::intValue))
        .mapToObj(UInt64::valueOf)
        .toList();
  }
}
