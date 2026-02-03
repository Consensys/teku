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

package tech.pegasys.teku.spec.logic.versions.fulu.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarTrackingKey;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError;
import tech.pegasys.teku.spec.logic.common.util.FuluTrackingKey;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

/**
 * Fulu-specific implementation of {@link DataColumnSidecarUtil}.
 *
 * <p>Implements Fulu gossip validation rules as per
 * https://github.com/ethereum/consensus-specs/blob/master/specs/fulu/p2p-interface.md#data_column_sidecar_subnet_id
 *
 * <p>Fulu sidecars include signed block headers
 */
public class DataColumnSidecarUtilFulu implements DataColumnSidecarUtil {

  private final MiscHelpersFulu miscHelpersFulu;

  public DataColumnSidecarUtilFulu(final MiscHelpersFulu miscHelpersFulu) {
    this.miscHelpersFulu = miscHelpersFulu;
  }

  /**
   * Perform slot timing gossip validation checks Gossip rule: [IGNORE] The sidecar is not from a
   * future slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance) -- i.e. validate that
   * block_header.slot <= current_slot (a client MAY queue future sidecars for processing at the
   * appropriate slot).
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isSlotFromFuture function to check if a slot is from the future
   * @return validation result, empty if it passes
   */
  @Override
  public Optional<DataColumnSidecarValidationError> performSlotTimingValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFromFuture) {
    final BeaconBlockHeader header =
        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader().getMessage();
    if (isSlotFromFuture.test(header.getSlot())) {
      return Optional.of(
          DataColumnSidecarValidationError.Timing.format(
              "DataColumnSidecar block header slot %s is from the future. It will be saved for future processing",
              header.getSlot()));
    }
    return Optional.empty();
  }

  /**
   * Perform slot finalization gossip validation checks. Gossip rule: [IGNORE] The sidecar is from a
   * slot greater than the latest finalized slot -- i.e. validate that block_header.slot >
   * compute_start_slot_at_epoch(state.finalized_checkpoint.epoch)
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isSlotFinalized function to check if a slot is finalized
   * @return validation result, empty if it passes
   */
  @Override
  public Optional<DataColumnSidecarValidationError> performSlotFinalizationValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFinalized) {
    final BeaconBlockHeader header =
        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader().getMessage();
    if (isSlotFinalized.test(header.getSlot())) {
      return Optional.of(
          DataColumnSidecarValidationError.Transient.format(
              "DataColumnSidecar is from slot %s greater than the latest finalized slot. Ignoring",
              header.getSlot()));
    }
    return Optional.empty();
  }

  /**
   * Check if the referenced block has been seen. Gossip rule: [IGNORE] The sidecar's block's parent
   * (defined by block_header.parent_root) has been seen (via gossip or non-gossip sources) (a
   * client MAY queue sidecars for processing once the parent block is retrieved).
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param isBlockRootSeen function to check if a block root has been seen
   * @return true if the referenced block has been seen
   */
  @Override
  public boolean isBlockParentSeen(
      final DataColumnSidecar dataColumnSidecar, final Function<Bytes32, Boolean> isBlockRootSeen) {
    final Optional<SignedBeaconBlockHeader> maybeSignedBlockHeader =
        dataColumnSidecar.getMaybeSignedBlockHeader();

    if (maybeSignedBlockHeader.isEmpty()) {
      return false;
    }

    final BeaconBlockHeader header = maybeSignedBlockHeader.get().getMessage();
    return isBlockRootSeen.apply(header.getParentRoot());
  }

  @Override
  public boolean isBlockSeen(
      final DataColumnSidecar dataColumnSidecar, final Function<Bytes32, Boolean> isBlockRootSeen) {
    // Non-applicable for FULU
    return true;
  }

  @Override
  public Optional<DataColumnSidecarValidationError> validateBlockSlot(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot) {
    // Fulu does not validate block slot match (this is Gloas-specific)
    return Optional.empty();
  }

  /**
   * Validate parent block for the data column sidecar.
   *
   * <p>Gossip rules:
   *
   * <ul>
   *   <li>[REJECT] The sidecar's block's parent (defined by block_header.parent_root) passes
   *       validation.
   *   <li>[REJECT] The sidecar is from a higher slot than the sidecar's block's parent (defined by
   *       block_header.parent_root).
   *   <li>[REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block -- i.e.
   *       get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch)
   *       == store.finalized_checkpoint.root.
   * </ul>
   *
   * @param dataColumnSidecar the data column sidecar
   * @param getBlockSlot function to get the block slot
   * @param invalidBlockRoots map of invalid block roots
   * @param currentFinalizedCheckpointIsAncestorOfBlock function to check finalized checkpoint
   *     ancestry
   * @return validation result
   */
  /*
   */
  @Override
  public Optional<DataColumnSidecarValidationError> validateParentBlock(
      final DataColumnSidecar dataColumnSidecar,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final Function<Bytes32, Optional<UInt64>> getBlockSlot,
      final BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock) {
    final SignedBeaconBlockHeader signedBlockHeader =
        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader();
    final BeaconBlockHeader blockHeader = signedBlockHeader.getMessage();
    final Optional<UInt64> maybeParentBlockSlot = getBlockSlot.apply(blockHeader.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      return Optional.of(
          DataColumnSidecarValidationError.Timing.format(
              "DataColumnSidecar block header parent block with root %s does not exist. It will be saved for future processing",
              blockHeader.getParentRoot()));
    }
    if (invalidBlockRoots.containsKey(blockHeader.getParentRoot())) {
      return Optional.of(
          DataColumnSidecarValidationError.Critical.format(
              "DataColumnSidecar block header parent with root %s is an invalid block",
              blockHeader.getParentRoot()));
    }

    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();
    if (!blockHeader.getSlot().isGreaterThan(parentBlockSlot)) {
      return Optional.of(
          DataColumnSidecarValidationError.Critical.format(
              "Parent block slot %s is after DataColumnSidecar slot %s",
              parentBlockSlot, blockHeader.getSlot()));
    }

    if (!currentFinalizedCheckpointIsAncestorOfBlock.test(
        blockHeader.getSlot(), blockHeader.getParentRoot())) {
      return Optional.of(
          DataColumnSidecarValidationError.Critical.format(
              "DataColumnSidecar block header at slot %s and with parent root %s does not descend from finalized checkpoint",
              blockHeader.getSlot(), blockHeader.getParentRoot()));
    }

    return Optional.empty();
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
    final DataColumnSidecarFulu fuluSidecar = DataColumnSidecarFulu.required(dataColumnSidecar);
    final BeaconBlockHeader header = fuluSidecar.getSignedBlockHeader().getMessage();
    return new FuluTrackingKey(
        header.getSlot(), header.getProposerIndex(), dataColumnSidecar.getIndex());
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
    return new FuluTrackingKey(
        header.getSlot(), header.getProposerIndex(), dataColumnSidecar.getIndex());
  }

  /**
   * Verify structural validity of the data column dataColumnSidecar. Gossip rule: [REJECT] The
   * sidecar is valid as verified by verify_data_column_sidecar(sidecar).
   *
   * @param dataColumnSidecar the data column dataColumnSidecar
   * @return true if structure is valid
   */
  @Override
  public boolean verifyDataColumnSidecarStructure(final DataColumnSidecar dataColumnSidecar) {
    return miscHelpersFulu.verifyDataColumnSidecar(dataColumnSidecar);
  }

  /**
   * Verify inclusion proof if applicable. Gossip rule: [REJECT] The sidecar's kzg_commitments field
   * inclusion proof is valid as verified by verify_data_column_sidecar_inclusion_proof(sidecar)
   *
   * @param dataColumnSidecar the data column sidecar
   * @param validInclusionProofInfoSet cache of previously validated inclusion proofs for
   *     optimization
   * @return true if inclusion proof is valid or not applicable
   */
  @Override
  public boolean verifyInclusionProof(
      final DataColumnSidecar dataColumnSidecar,
      final Set<InclusionProofInfo> validInclusionProofInfoSet) {
    final DataColumnSidecarFulu fuluSidecar = DataColumnSidecarFulu.required(dataColumnSidecar);

    // Check cache first for optimization
    final InclusionProofInfo proofInfo =
        new InclusionProofInfo(
            dataColumnSidecar.getKzgCommitments().hashTreeRoot(),
            fuluSidecar.getKzgCommitmentsInclusionProof().hashTreeRoot(),
            fuluSidecar.getBlockBodyRoot());

    if (validInclusionProofInfoSet.contains(proofInfo)) {
      return true;
    }

    // Verify inclusion proof
    try {
      return miscHelpersFulu.verifyDataColumnSidecarInclusionProof(dataColumnSidecar);
    } catch (final Throwable t) {
      return false;
    }
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
    return miscHelpersFulu.verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
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
    final SignedBeaconBlockHeader signedBlockHeader =
        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader();
    final BeaconBlockHeader header = signedBlockHeader.getMessage();

    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(state),
            state.getFork(),
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(header, domain);

    return Optional.of(
        new SignatureVerificationData(
            signingRoot, header.getProposerIndex(), signedBlockHeader.getSignature(), state));
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
    final DataColumnSidecarFulu dataColumnSidecarFulu =
        DataColumnSidecarFulu.required(dataColumnSidecar);

    // Cache signed block header hash for known valid header optimization
    validSignedBlockHeaders.add(dataColumnSidecarFulu.getSignedBlockHeader().hashTreeRoot());

    // Cache inclusion proof info for future validations
    validInclusionProofInfoSet.add(
        new InclusionProofInfo(
            dataColumnSidecar.getKzgCommitments().hashTreeRoot(),
            dataColumnSidecarFulu.getKzgCommitmentsInclusionProof().hashTreeRoot(),
            dataColumnSidecarFulu.getBlockBodyRoot()));
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecarValidationError>> validateWithBlock(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, SafeFuture<Optional<BeaconBlock>>> retrieveBlockByRoot) {
    // Fulu sidecars already contain the header, no block validation needed
    return SafeFuture.completedFuture(Optional.empty());
  }

  /**
   * Perform state-dependent validation for Fulu data column sidecars. Validates proposer
   * correctness and header signature by retrieving the parent block's post state. Implements the
   * following gossip rules:
   *
   * <ul>
   *   <li>[REJECT] The sidecar is proposed by the expected proposer_index for the block's slot in
   *       the context of the current shuffling (defined by
   *       block_header.parent_root/block_header.slot). If the proposer_index cannot immediately be
   *       verified against the expected shuffling, the sidecar MAY be queued for later processing
   *       while proposers for the block's branch are calculated -- in such a case do not REJECT,
   *       instead IGNORE this message.
   *   <li>[REJECT] The proposer signature of sidecar.signed_block_header is valid with respect to
   *       the block_header.proposer_index pubkey.
   * </ul>
   *
   * <p>Optimization: If the signed block header has been fully validated before (cached in
   * validSignedBlockHeaders), all state-dependent checks are skipped.
   *
   * @param dataColumnSidecar the data column sidecar to validate
   * @param spec the Spec instance for domain and signing root computation
   * @param validInclusionProofInfoSet cache of validated inclusion proofs for optimization
   * @param validSignedBlockHeaders cache of validated signed block header hashes for optimization
   * @param getBlockSlot function to get the slot for a block root
   * @param retrieveBeaconState function to retrieve beacon state at a specific slot/block
   * @param isProposerTheExpectedProposer function to check if proposer index matches expected
   * @param isSignatureValidWithRespectToProposerIndex function to verify proposer signature
   * @return SafeFuture with optional validation error. Empty means validation passed, present means
   *     validation found an issue (invalid/ignore/save for future).
   */
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
    final SignedBeaconBlockHeader signedBlockHeader =
        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader();
    final BeaconBlockHeader blockHeader = signedBlockHeader.getMessage();
    // Optimization: If we have already completely verified a sidecar with the same signed header
    if (validSignedBlockHeaders.contains(signedBlockHeader.hashTreeRoot())) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final Bytes32 parentBlockRoot = blockHeader.getParentRoot();
    final Optional<UInt64> maybeParentBlockSlot = getBlockSlot.apply(parentBlockRoot);
    if (maybeParentBlockSlot.isEmpty()) {
      return SafeFuture.completedFuture(
          Optional.of(
              DataColumnSidecarValidationError.Timing.format(
                  "DataColumnSidecar parent block with root %s is unavailable. Saving for future processing",
                  parentBlockRoot)));
    }
    return retrieveBeaconState
        .apply(
            new StateRetrievalData(
                maybeParentBlockSlot.get(), parentBlockRoot, dataColumnSidecar.getSlot()))
        .thenApply(
            maybePostState -> {
              if (maybePostState.isEmpty()) {
                return Optional.of(
                    DataColumnSidecarValidationError.Transient.format(
                        "DataColumnSidecar block header state at slot %s wasn't available.",
                        dataColumnSidecar.getSlot()));
              }
              final BeaconState postState = maybePostState.get();

              /*
               * [REJECT] The sidecar is proposed by the expected proposer_index for the block's slot
               * in the context of the current shuffling (defined by block_header.parent_root/block_header.slot).
               * If the proposer_index cannot immediately be verified against the expected shuffling,
               * the sidecar MAY be queued for later processing while proposers for the block's branch
               * are calculated -- in such a case do not REJECT, instead IGNORE this message.
               */
              if (!isProposerTheExpectedProposer.apply(
                  new ProposerValidationData(
                      blockHeader.getProposerIndex(), blockHeader.getSlot(), postState))) {
                return Optional.of(
                    DataColumnSidecarValidationError.Critical.format(
                        "DataColumnSidecar block header proposed by incorrect proposer with index %s",
                        blockHeader.getProposerIndex()));
              }

              /*
               * [REJECT] The proposer signature of sidecar.signed_block_header,
               * is valid with respect to the block_header.proposer_index pubkey.
               */
              final Optional<DataColumnSidecarUtil.SignatureVerificationData> maybeSignatureData =
                  getSignatureVerificationData(spec, postState, dataColumnSidecar);
              if (maybeSignatureData.isPresent()) {
                final DataColumnSidecarUtil.SignatureVerificationData signatureData =
                    maybeSignatureData.get();
                if (!isSignatureValidWithRespectToProposerIndex.apply(signatureData)) {
                  return Optional.of(
                      DataColumnSidecarValidationError.Critical.format(
                          "DataColumnSidecar block header signature is invalid"));
                }
              }

              // Cache validated info for optimization (tracking key added by caller after all
              // validations)
              cacheValidatedInfo(
                  dataColumnSidecar, validSignedBlockHeaders, validInclusionProofInfoSet);

              return Optional.empty();
            });
  }
}
