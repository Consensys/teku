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
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarTrackingKey;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationResult;
import tech.pegasys.teku.spec.logic.common.util.GloasTrackingKey;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

/**
 * Gloas-specific implementation of {@link DataColumnSidecarUtil}.
 *
 * <p>Implements Gloas gossip validation rules as per
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#data_column_sidecar_subnet_id
 *
 * <p>Gloas sidecars do NOT include signed block headers, resulting in simplified validation
 */
public class DataColumnSidecarUtilGloas implements DataColumnSidecarUtil {

  /**
   * Perform slot timing gossip validation checks
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isSlotFromFuture function to check if a slot is from the future
   * @return validation result, empty if it passes
   */
  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotTimingValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFromFuture) {
    return Optional.empty();
  }

  /**
   * Perform slot finalization gossip validation checks
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isSlotFinalized function to check if a slot is finalized
   * @return validation result, empty if it passes
   */
  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotFinalizationValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFinalized) {
    return Optional.empty();
  }

  /**
   * Check if the sidecar's block parent has been seen. Gossip rule: Not applicable to Gloas as
   * Gloas sidecars don't contain block headers with parent references.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isBlockRootSeen function to check if a block root has been seen
   * @return true (always valid for Gloas)
   */
  @Override
  public boolean isBlockParentSeen(
      final DataColumnSidecar dataColumnSidecar, final Function<Bytes32, Boolean> isBlockRootSeen) {
    return true;
  }

  /**
   * Check if the referenced block has been seen. Gossip rule: [IGNORE] The sidecar's
   * beacon_block_root has been seen via a valid signed execution payload bid. A client MAY queue
   * the sidecar for processing once the block is retrieved.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isBlockRootSeen function to check if a block root has been seen
   * @return true if the referenced block has been seen
   */
  @Override
  public boolean isBlockWithBidSeen(
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
  public DataColumnSidecarValidationResult validateBlockSlot(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot) {
    final Bytes32 beaconBlockRoot = dataColumnSidecar.getBeaconBlockRoot();
    final Optional<UInt64> blockSlot = getSlotForBlockRoot.apply(beaconBlockRoot);
    if (blockSlot.isEmpty()) {
      return DataColumnSidecarValidationResult.invalid(
          "DataColumnSidecar's beacon_block_root does not correspond to a known block");
    }
    if (!blockSlot.get().equals(dataColumnSidecar.getSlot())) {
      return DataColumnSidecarValidationResult.invalid(
          () ->
              String.format(
                  "DataColumnSidecar's slot %s does not match the block slot %s for beacon_block_root %s",
                  dataColumnSidecar.getSlot(), blockSlot.get(), beaconBlockRoot));
    }
    return DataColumnSidecarValidationResult.valid();
  }

  /**
   * Validate parent block for the data column sidecar.
   *
   * @param dataColumnSidecar the block header from the sidecar
   * @param parentBlockSlot the slot of the parent block
   * @param invalidBlockRoots map of invalid block roots
   * @param currentFinalizedCheckpointIsAncestorOfBlock function to check finalized checkpoint
   *     ancestry
   * @return validation result
   */
  @Override
  public DataColumnSidecarValidationResult validateParentBlock(
      final BeaconBlockHeader dataColumnSidecar,
      final UInt64 parentBlockSlot,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock) {
    // Gloas does not validate parent block (no header in Gloas sidecars)
    return DataColumnSidecarValidationResult.valid();
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
   * @param specLogic the fork-specific SpecLogic containing MiscHelpers
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return true if structure is valid
   */
  @Override
  public boolean verifyDataColumnSidecarStructure(
      final SpecLogic specLogic, final DataColumnSidecar dataColumnSidecar) {
    final MiscHelpersGloas miscHelpersGloas = MiscHelpersGloas.required(specLogic.miscHelpers());
    return miscHelpersGloas.verifyDataColumnSidecar(dataColumnSidecar);
  }

  /**
   * Verify inclusion proof if applicable.
   *
   * @param specLogic the fork-specific SpecLogic containing MiscHelpers
   * @param dataColumnSidecar the data column sidecar
   * @param validInclusionProofInfoSet cache of previously validated inclusion proofs for
   *     optimization
   * @return true if inclusion proof is valid or not applicable
   */
  @Override
  public boolean verifyInclusionProof(
      final SpecLogic specLogic,
      final DataColumnSidecar dataColumnSidecar,
      final Set<InclusionProofInfo> validInclusionProofInfoSet) {
    // Gloas doesn't have inclusion proof requirement (no header in Gloas sidecars)
    return true;
  }

  /**
   * Verify KZG proofs for the data column sidecar. Gossip rule: [REJECT] The sidecar's column data
   * is valid as verified by verify_data_column_sidecar_kzg_proofs(sidecar)
   *
   * @param specLogic the fork-specific SpecLogic containing MiscHelpers
   * @param dataColumnSidecar the data column sidecar
   * @return true if KZG proofs are valid
   */
  @Override
  public boolean verifyDataColumnSidecarKzgProofs(
      final SpecLogic specLogic, final DataColumnSidecar dataColumnSidecar) {
    final MiscHelpersGloas miscHelpersGloas = MiscHelpersGloas.required(specLogic.miscHelpers());
    return miscHelpersGloas.verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
  }

  /**
   * Get signature verification data if applicable.
   *
   * @param spec the Spec instance for domain and signing root computation
   * @param state the beacon state for proposer lookup
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return Optional containing signature verification data if applicable
   */
  @Override
  public Optional<SignatureVerificationData> getSignatureVerificationData(
      final Spec spec, final BeaconState state, final DataColumnSidecar dataColumnSidecar) {
    // Gloas doesn't have header signature requirement
    return Optional.empty();
  }

  /**
   * Get beacon block header if applicable.
   *
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return Optional containing the header if applicable
   */
  @Override
  public Optional<BeaconBlockHeader> getBlockHeader(final DataColumnSidecar dataColumnSidecar) {
    // Gloas sidecars don't contain block headers
    return Optional.empty();
  }

  /**
   * Cache validated header/proof info for optimization if applicable.
   *
   * @param dataColumnSidecar the validated data column dataColumnSidecar
   * @param validSignedBlockHeaders cache of validated signed block header hashes
   * @param validInclusionProofInfoSet cache of validated inclusion proof info
   */
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
  public SafeFuture<Optional<DataColumnSidecarValidationResult>> validateBidKzgCommitmentsRoot(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, SafeFuture<Optional<BeaconBlock>>> retrieveBlockByRoot) {

    final Bytes32 beaconBlockRoot = dataColumnSidecar.getBeaconBlockRoot();

    return retrieveBlockByRoot
        .apply(beaconBlockRoot)
        .thenApply(
            maybeBeaconBlock -> {
              if (maybeBeaconBlock.isEmpty()) {
                return Optional.of(
                    DataColumnSidecarValidationResult.saveForFuture(
                        "DataColumnSidecar's beacon_block_root does not correspond to a known block"));
              }
              final BeaconBlock beaconBlock = maybeBeaconBlock.get();
              final DataColumnSidecarValidationResult kzgCommitmentsRootValidationResult =
                  validateKzgCommitmentsRoot(dataColumnSidecar, beaconBlock);
              if (!kzgCommitmentsRootValidationResult.isValid()) {
                return Optional.of(kzgCommitmentsRootValidationResult);
              }
              return Optional.empty();
            });
  }

  private DataColumnSidecarValidationResult validateKzgCommitmentsRoot(
      final DataColumnSidecar dataColumnSidecar, final BeaconBlock beaconBlock) {

    final Optional<SignedExecutionPayloadBid> maybeSignedExecutionPayloadBid =
        beaconBlock.getBody().getOptionalSignedExecutionPayloadBid();
    if (maybeSignedExecutionPayloadBid.isEmpty()) {
      return DataColumnSidecarValidationResult.invalid(
          "Missing DataColumnSidecar's corresponding payload execution bid");
    }

    final Bytes32 kzgCommitmentsRoot =
        maybeSignedExecutionPayloadBid.get().getMessage().getBlobKzgCommitmentsRoot();
    final Bytes32 dataColumnSidecarCommitmentsRoot =
        dataColumnSidecar.getKzgCommitments().hashTreeRoot();
    if (!kzgCommitmentsRoot.equals(dataColumnSidecarCommitmentsRoot)) {
      return DataColumnSidecarValidationResult.invalid(
          () ->
              String.format(
                  "DataColumnSidecar's KZG commitments root %s does not match the bid's blob_kzg_commitments_root %s",
                  dataColumnSidecarCommitmentsRoot, kzgCommitmentsRoot));
    }
    return DataColumnSidecarValidationResult.valid();
  }
}
