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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

/**
 * Fork-aware utility for Data Column Sidecar gossip validation. This interface defines the contract
 * for fork-specific validation logic.
 *
 * <p>Different fork implementations (Fulu, Gloas) provide fork-specific behavior. Methods that
 * don't apply to a particular fork return {@link DataColumnSidecarValidationResult#VALID}.
 */
public interface DataColumnSidecarUtil {

  /**
   * Perform slot timing gossip validation checks
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isSlotFromFuture function to check if a slot is from the future
   * @return validation result if the sidecar should be ignored or saved, empty if it passes
   */
  Optional<SlotInclusionGossipValidationResult> performSlotTimingValidation(
      DataColumnSidecar dataColumnSidecar, Predicate<UInt64> isSlotFromFuture);

  /**
   * Perform slot finalization gossip validation checks
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isSlotFinalized function to check if a slot is finalized
   * @return validation result if the sidecar should be ignored or saved, empty if it passes
   */
  Optional<SlotInclusionGossipValidationResult> performSlotFinalizationValidation(
      DataColumnSidecar dataColumnSidecar, Predicate<UInt64> isSlotFinalized);

  /**
   * Check if the referenced block has been seen.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isBlockRootSeen function to check if a block root has been seen
   * @return true if the referenced block has been seen
   */
  boolean isBlockSeen(
      DataColumnSidecar dataColumnSidecar, Function<Bytes32, Boolean> isBlockRootSeen);

  /**
   * Validate that the sidecar's KZG commitments root matches the block's KZG commitments root.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param getBlockKzgCommitmentsRoot function to get the block's kzg commitments root by block
   *     root
   * @return validation result
   */
  DataColumnSidecarValidationResult validateKzgCommitmentsRoot(
      DataColumnSidecar dataColumnSidecar,
      Function<Bytes32, Optional<Bytes32>> getBlockKzgCommitmentsRoot);

  /**
   * Validate that the sidecar's slot matches the referenced block's slot.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param getSlotForBlockRoot function to get the slot for a block root
   * @return validation result
   */
  DataColumnSidecarValidationResult validateBlockSlot(
      DataColumnSidecar dataColumnSidecar, Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot);

  /**
   * Validate parent block for the data column sidecar.
   *
   * @param blockHeader the block header from the sidecar
   * @param parentBlockSlot the slot of the parent block
   * @param invalidBlockRoots map of invalid block roots
   * @param currentFinalizedCheckpointIsAncestorOfBlock function to check finalized checkpoint
   *     ancestry
   * @return validation result
   */
  DataColumnSidecarValidationResult validateParentBlock(
      BeaconBlockHeader blockHeader,
      UInt64 parentBlockSlot,
      Map<Bytes32, BlockImportResult> invalidBlockRoots,
      BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock);

  /**
   * Extract the tracking key from a data column dataColumnSidecar.
   *
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return the fork-appropriate tracking key
   */
  DataColumnSidecarTrackingKey extractTrackingKey(DataColumnSidecar dataColumnSidecar);

  /**
   * Extract tracking key from block header and dataColumnSidecar for late validation check.
   *
   * @param header the beacon block header (may be null for Gloas)
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return the fork-appropriate tracking key
   */
  DataColumnSidecarTrackingKey extractTrackingKeyFromHeader(
      BeaconBlockHeader header, DataColumnSidecar dataColumnSidecar);

  /**
   * Verify structural validity of the data column dataColumnSidecar.
   *
   * @param specLogic the fork-specific SpecLogic containing MiscHelpers
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return true if structure is valid
   */
  boolean verifyDataColumnSidecarStructure(
      SpecLogic specLogic, DataColumnSidecar dataColumnSidecar);

  /**
   * Verify inclusion proof if applicable.
   *
   * @param specLogic the fork-specific SpecLogic containing MiscHelpers
   * @param dataColumnSidecar the data column sidecar
   * @param validInclusionProofInfoSet cache of previously validated inclusion proofs for
   *     optimization
   * @return true if inclusion proof is valid or not applicable
   */
  boolean verifyInclusionProof(
      SpecLogic specLogic,
      DataColumnSidecar dataColumnSidecar,
      Set<InclusionProofInfo> validInclusionProofInfoSet);

  /**
   * Verify KZG proofs for the data column sidecar.
   *
   * @param specLogic the fork-specific SpecLogic containing MiscHelpers
   * @param dataColumnSidecar the data column sidecar
   * @return true if KZG proofs are valid
   */
  boolean verifyDataColumnSidecarKzgProofs(
      SpecLogic specLogic, DataColumnSidecar dataColumnSidecar);

  /**
   * Get signature verification data if applicable.
   *
   * @param spec the Spec instance for domain and signing root computation
   * @param state the beacon state for proposer lookup
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return Optional containing signature verification data if applicable
   */
  Optional<SignatureVerificationData> getSignatureVerificationData(
      Spec spec, BeaconState state, DataColumnSidecar dataColumnSidecar);

  /**
   * Get beacon block header if applicable.
   *
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return Optional containing the header if applicable
   */
  Optional<BeaconBlockHeader> getBlockHeader(DataColumnSidecar dataColumnSidecar);

  /**
   * Cache validated header/proof info for optimization if applicable.
   *
   * @param dataColumnSidecar the validated data column dataColumnSidecar
   * @param validSignedBlockHeaders cache of validated signed block header hashes
   * @param validInclusionProofInfoSet cache of validated inclusion proof info
   */
  void cacheValidatedInfo(
      DataColumnSidecar dataColumnSidecar,
      Set<Bytes32> validSignedBlockHeaders,
      Set<InclusionProofInfo> validInclusionProofInfoSet);

  /**
   * Helper record for caching inclusion proof validation results.
   *
   * @param commitmentsRoot hash tree root of KZG commitments
   * @param inclusionProofRoot hash tree root of inclusion proof
   * @param bodyRoot the beacon block body root
   */
  record InclusionProofInfo(
      Bytes32 commitmentsRoot, Bytes32 inclusionProofRoot, Bytes32 bodyRoot) {}

  /**
   * Helper record for signature verification data.
   *
   * @param signingRoot the signing root to verify
   * @param proposerIndex the proposer index
   * @param signature the BLS signature to verify
   */
  record SignatureVerificationData(
      Bytes signingRoot, UInt64 proposerIndex, BLSSignature signature) {}

  /** Result of slot inclusion gossip validation. */
  enum SlotInclusionGossipValidationResult {
    IGNORE,
    SAVE_FOR_FUTURE
  }
}
