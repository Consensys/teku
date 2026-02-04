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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarTrackingKey;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError;
import tech.pegasys.teku.spec.logic.common.util.GloasTrackingKey;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

/**
 * Gloas-specific implementation of {@link DataColumnSidecarUtil}.
 *
 * <p>Implements Gloas gossip validation rules as per <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#data_column_sidecar_subnet_id">data_column_sidecar_subnet_id
 * p2p gossip rules</a>
 *
 * <p>Gloas sidecars do NOT include signed block headers, resulting in simplified validation
 */
public class DataColumnSidecarUtilGloas implements DataColumnSidecarUtil {

  private final MiscHelpersGloas miscHelpersGloas;

  public DataColumnSidecarUtilGloas(final MiscHelpersGloas miscHelpersGloas) {
    this.miscHelpersGloas = miscHelpersGloas;
  }

  @Override
  public Optional<DataColumnSidecarValidationError> performSlotTimingValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFromFuture) {
    // Non applicable for Gloas
    return Optional.empty();
  }

  @Override
  public Optional<DataColumnSidecarValidationError> performSlotFinalizationValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFinalized) {
    // Non applicable for Gloas
    return Optional.empty();
  }

  @Override
  public boolean isBlockParentSeen(
      final DataColumnSidecar dataColumnSidecar, final Function<Bytes32, Boolean> isBlockRootSeen) {
    // Not applicable to Gloas as sidecars don't contain block headers with parent references.
    return true;
  }

  /**
   * Check if the referenced block has been seen. Gossip rule: [IGNORE] A valid block for the
   * sidecar's slot has been seen (via gossip or non-gossip sources). If not yet seen, a client MUST
   * queue the sidecar for deferred validation and possible processing once the block is received or
   * retrieved.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isBlockRootSeen function to check if a block root has been seen
   * @return true if the referenced block has been seen
   */
  @Override
  public boolean isBlockSeen(
      final DataColumnSidecar dataColumnSidecar, final Function<Bytes32, Boolean> isBlockRootSeen) {
    return isBlockRootSeen.apply(dataColumnSidecar.getBeaconBlockRoot());
  }

  /**
   * Validate that the sidecar's slot matches the referenced block's slot. Gossip rule: [REJECT] The
   * sidecar's slot matches the slot of the block with root beacon_block_root
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param getSlotForBlockRoot function to get the slot for a block root
   * @return validation result
   */
  @Override
  public Optional<DataColumnSidecarValidationError> validateBlockSlot(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot) {
    final Bytes32 beaconBlockRoot = dataColumnSidecar.getBeaconBlockRoot();
    final Optional<UInt64> blockSlot = getSlotForBlockRoot.apply(beaconBlockRoot);
    if (blockSlot.isEmpty()) {
      return Optional.of(
          DataColumnSidecarValidationError.BadTiming.format(
              "DataColumnSidecar's beacon_block_root %s does not correspond to a known block. It will be saved for future processing",
              beaconBlockRoot));
    }
    if (!blockSlot.get().equals(dataColumnSidecar.getSlot())) {
      return Optional.of(
          DataColumnSidecarValidationError.Critical.format(
              "DataColumnSidecar's slot %s does not match the block slot %s for beacon_block_root %s",
              dataColumnSidecar.getSlot(), blockSlot.get(), beaconBlockRoot));
    }
    return Optional.empty();
  }

  @Override
  public Optional<DataColumnSidecarValidationError> validateParentBlock(
      final DataColumnSidecar dataColumnSidecar,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final Function<Bytes32, Optional<UInt64>> getBlockSlot,
      final BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock) {
    // Gloas does not validate parent block (no header in Gloas sidecars)
    return Optional.empty();
  }

  /**
   * Extract tracking key from block header and dataColumnSidecar for late validation check.
   *
   * @param header the beacon block header (may be null for Gloas)
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return the fork-appropriate tracking key
   */
  @Override
  public DataColumnSidecarTrackingKey extractTrackingKeyFromHeader(
      final BeaconBlockHeader header, final DataColumnSidecar dataColumnSidecar) {
    return extractTrackingKey(dataColumnSidecar);
  }

  /**
   * Extract the tracking key from a data column dataColumnSidecar.
   *
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return the fork-appropriate tracking key
   */
  @Override
  public DataColumnSidecarTrackingKey extractTrackingKey(
      final DataColumnSidecar dataColumnSidecar) {
    return new GloasTrackingKey(
        dataColumnSidecar.getBeaconBlockRoot(), dataColumnSidecar.getIndex());
  }

  /**
   * Verify structural validity of the data column dataColumnSidecar.
   *
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return true if structure is valid
   */
  @Override
  public boolean verifyDataColumnSidecarStructure(final DataColumnSidecar dataColumnSidecar) {
    return miscHelpersGloas.verifyDataColumnSidecar(dataColumnSidecar);
  }

  @Override
  public boolean verifyInclusionProof(
      final DataColumnSidecar dataColumnSidecar,
      final Set<InclusionProofInfo> validInclusionProofInfoSet) {
    // Gloas doesn't have inclusion proof requirement (no header in Gloas sidecars)
    return true;
  }

  /**
   * Verify KZG proofs for the data column sidecar. Gossip rule: [REJECT] The sidecar's column data
   * is valid as verified by verify_data_column_sidecar_kzg_proofs(sidecar)
   *
   * @param dataColumnSidecar the data column sidecar
   * @return true if KZG proofs are valid
   */
  @Override
  public boolean verifyDataColumnSidecarKzgProofs(final DataColumnSidecar dataColumnSidecar) {
    return miscHelpersGloas.verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
  }

  @Override
  public Optional<SignatureVerificationData> getSignatureVerificationData(
      final Spec spec, final BeaconState state, final DataColumnSidecar dataColumnSidecar) {
    // Gloas doesn't have header signature requirement
    return Optional.empty();
  }

  @Override
  public void cacheValidatedInfo(
      final DataColumnSidecar dataColumnSidecar,
      final Set<Bytes32> validSignedBlockHeaders,
      final Set<InclusionProofInfo> validInclusionProofInfoSet) {
    // Nothing to cache for Gloas (no header, no inclusion proof)
  }

  /**
   * Perform async kzg commitments root validation for Gloas. Gossip rule: [REJECT] The hash of the
   * sidecar's kzg_commitments matches the blob_kzg_commitments_root in the corresponding builder's
   * bid for sidecar.beacon_block_root.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param retrieveBlockByRoot function to retrieve full block by root (potentially expensive)
   * @return SafeFuture with optional validation result. Empty means validation passed, Present
   *     means validation found an issue (invalid/save for future).
   */
  @Override
  public SafeFuture<Optional<DataColumnSidecarValidationError>> validateWithBlock(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, SafeFuture<Optional<BeaconBlock>>> retrieveBlockByRoot) {

    final Bytes32 beaconBlockRoot = dataColumnSidecar.getBeaconBlockRoot();

    return retrieveBlockByRoot
        .apply(beaconBlockRoot)
        .thenApply(
            maybeBeaconBlock -> {
              if (maybeBeaconBlock.isEmpty()) {
                return Optional.of(
                    DataColumnSidecarValidationError.BadTiming.format(
                        "DataColumnSidecar's beacon_block_root %s does not correspond to a known block",
                        beaconBlockRoot));
              }
              final BeaconBlock beaconBlock = maybeBeaconBlock.get();
              return validateKzgCommitmentsRoot(dataColumnSidecar, beaconBlock);
            });
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecarValidationError>> validateWithState(
      final DataColumnSidecar dataColumnSidecar,
      final Spec spec,
      final Set<InclusionProofInfo> validInclusionProofInfoSet,
      final Set<Bytes32> validSignedBlockHeaders,
      final Function<Bytes32, Optional<UInt64>> getBlockSlot,
      final Function<StateRetrievalData, SafeFuture<Optional<BeaconState>>> retrieveBeaconState,
      final Function<ProposerValidationData, Boolean> isProposerTheExpectedProposer,
      final Function<SignatureVerificationData, Boolean>
          isSignatureValidWithRespectToProposerIndex) {
    // Non-applicable for Gloas, no state validation required
    return SafeFuture.completedFuture(Optional.empty());
  }

  private Optional<DataColumnSidecarValidationError> validateKzgCommitmentsRoot(
      final DataColumnSidecar dataColumnSidecar, final BeaconBlock beaconBlock) {
    final Bytes32 kzgCommitmentsRoot =
        BeaconBlockBodyGloas.required(beaconBlock.getBody())
            .getSignedExecutionPayloadBid()
            .getMessage()
            .getBlobKzgCommitmentsRoot();
    final Bytes32 dataColumnSidecarCommitmentsRoot =
        dataColumnSidecar.getKzgCommitments().hashTreeRoot();
    if (!kzgCommitmentsRoot.equals(dataColumnSidecarCommitmentsRoot)) {
      return Optional.of(
          DataColumnSidecarValidationError.Critical.format(
              "DataColumnSidecar's KZG commitments root %s does not match the bid's blob_kzg_commitments_root %s",
              dataColumnSidecarCommitmentsRoot, kzgCommitmentsRoot));
    }
    return Optional.empty();
  }
}
