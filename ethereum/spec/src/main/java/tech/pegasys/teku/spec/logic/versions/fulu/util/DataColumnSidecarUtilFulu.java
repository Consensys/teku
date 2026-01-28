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

package tech.pegasys.teku.spec.logic.versions.fulu.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarTrackingKey;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationResult;
import tech.pegasys.teku.spec.logic.common.util.FuluTrackingKey;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

/**
 * Fulu-specific implementation of {@link DataColumnSidecarUtil}.
 *
 * <p>Implements Fulu gossip validation rules as per
 * https://github.com/ethereum/consensus-specs/blob/master/specs/fulu/p2p-interface.md#data_column_sidecar_subnet_id
 *
 * <p>Fulu sidecars include signed block headers, which require: - Slot/finalization checks - Parent
 * block availability and validation - Inclusion proof verification - Proposer verification -
 * Signature verification
 */
public class DataColumnSidecarUtilFulu implements DataColumnSidecarUtil {

  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotTimingValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFromFuture) {
    final Optional<SignedBeaconBlockHeader> maybeSignedBlockHeader =
        dataColumnSidecar.getMaybeSignedBlockHeader();

    if (maybeSignedBlockHeader.isEmpty()) {
      return Optional.of(SlotInclusionGossipValidationResult.IGNORE);
    }

    final BeaconBlockHeader header = maybeSignedBlockHeader.get().getMessage();

    /*
     * [IGNORE] The sidecar is not from a future slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance)
     * -- i.e. validate that block_header.slot <= current_slot
     * (a client MAY queue future sidecars for processing at the appropriate slot).
     */

    if (isSlotFromFuture.test(header.getSlot())) {
      return Optional.of(SlotInclusionGossipValidationResult.SAVE_FOR_FUTURE);
    }

    return Optional.empty();
  }

  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotFinalizationValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFinalized) {
    final Optional<SignedBeaconBlockHeader> maybeSignedBlockHeader =
        dataColumnSidecar.getMaybeSignedBlockHeader();

    if (maybeSignedBlockHeader.isEmpty()) {
      return Optional.of(SlotInclusionGossipValidationResult.IGNORE);
    }

    final BeaconBlockHeader header = maybeSignedBlockHeader.get().getMessage();

    /*
     * [IGNORE] The sidecar is from a slot greater than the latest finalized slot
     * -- i.e. validate that block_header.slot > compute_start_slot_at_epoch(state.finalized_checkpoint.epoch)
     */

    if (isSlotFinalized.test(header.getSlot())) {
      return Optional.of(SlotInclusionGossipValidationResult.IGNORE);
    }

    return Optional.empty();
  }

  /*
   * [IGNORE] The sidecar's block's parent (defined by block_header.parent_root) has been seen
   * (via gossip or non-gossip sources)
   * (a client MAY queue sidecars for processing once the parent block is retrieved).
   */
  @Override
  public boolean isBlockSeen(
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
  public DataColumnSidecarValidationResult validateKzgCommitmentsRoot(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, Optional<Bytes32>> getBlockKzgCommitmentsRoot) {
    // Fulu does not validate the kzg commitments root (this is Gloas-specific)
    return DataColumnSidecarValidationResult.valid();
  }

  @Override
  public DataColumnSidecarValidationResult validateBlockSlotMatch(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot) {
    // Fulu does not validate block slot match (this is Gloas-specific)
    return DataColumnSidecarValidationResult.valid();
  }

  /*
   * [REJECT] The sidecar's block's parent (defined by block_header.parent_root) passes validation.
   * [REJECT] The sidecar is from a higher slot than the sidecar's block's parent (defined by block_header.parent_root).
   * [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block
   * -- i.e. get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root.
   */
  @Override
  public DataColumnSidecarValidationResult validateParentBlock(
      final BeaconBlockHeader blockHeader,
      final Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock) {

    final Optional<UInt64> maybeParentBlockSlot =
        getSlotForBlockRoot.apply(blockHeader.getParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      return DataColumnSidecarValidationResult.invalid(
          "DataColumnSidecar block header parent block does not exist");
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    /*
     * [REJECT] The sidecar's block's parent (defined by block_header.parent_root) passes validation.
     */
    if (invalidBlockRoots.containsKey(blockHeader.getParentRoot())) {
      return DataColumnSidecarValidationResult.invalid(
          "DataColumnSidecar block header has an invalid parent root");
    }

    /*
     * [REJECT] The sidecar is from a higher slot than the sidecar's block's parent (defined by block_header.parent_root).
     */
    if (!blockHeader.getSlot().isGreaterThan(parentBlockSlot)) {
      return DataColumnSidecarValidationResult.invalid(
          "Parent block slot is after DataColumnSidecar slot");
    }

    /*
     * [REJECT] The current finalized_checkpoint is an ancestor of the sidecar's block
     * -- i.e. get_checkpoint_block(store, block_header.parent_root, store.finalized_checkpoint.epoch) == store.finalized_checkpoint.root.
     */
    if (!currentFinalizedCheckpointIsAncestorOfBlock.test(
        blockHeader.getSlot(), blockHeader.getParentRoot())) {
      return DataColumnSidecarValidationResult.invalid(
          "DataColumnSidecar block header does not descend from finalized checkpoint");
    }

    return DataColumnSidecarValidationResult.valid();
  }

  @Override
  public DataColumnSidecarTrackingKey extractTrackingKey(
      final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarFulu fuluSidecar = DataColumnSidecarFulu.required(dataColumnSidecar);
    final BeaconBlockHeader header = fuluSidecar.getSignedBlockHeader().getMessage();
    return new FuluTrackingKey(
        header.getSlot(), header.getProposerIndex(), dataColumnSidecar.getIndex());
  }

  @Override
  public DataColumnSidecarTrackingKey extractTrackingKeyFromHeader(
      final BeaconBlockHeader header, final DataColumnSidecar dataColumnSidecar) {
    return new FuluTrackingKey(
        header.getSlot(), header.getProposerIndex(), dataColumnSidecar.getIndex());
  }

  @Override
  public boolean verifyDataColumnSidecarStructure(
      final SpecLogic specLogic, final DataColumnSidecar dataColumnSidecar) {
    final MiscHelpersFulu miscHelpersFulu = MiscHelpersFulu.required(specLogic.miscHelpers());
    return miscHelpersFulu.verifyDataColumnSidecar(dataColumnSidecar);
  }

  /*
   * [REJECT] The sidecar's kzg_commitments field inclusion proof is valid as verified by
   *   verify_data_column_sidecar_inclusion_proof(sidecar)
   */
  @Override
  public boolean verifyInclusionProof(
      final SpecLogic specLogic,
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
      final MiscHelpersFulu miscHelpersFulu = MiscHelpersFulu.required(specLogic.miscHelpers());
      return miscHelpersFulu.verifyDataColumnSidecarInclusionProof(dataColumnSidecar);
    } catch (final Throwable t) {
      return false;
    }
  }

  /*
   * [REJECT] The sidecar's column data is valid as verified by verify_data_column_sidecar_kzg_proofs(sidecar)
   */
  @Override
  public boolean verifyDataColumnSidecarKzgProofs(
      final SpecLogic specLogic, final DataColumnSidecar dataColumnSidecar) {
    final MiscHelpersFulu miscHelpersFulu = MiscHelpersFulu.required(specLogic.miscHelpers());
    return miscHelpersFulu.verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
  }

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
            signingRoot, header.getProposerIndex(), signedBlockHeader.getSignature()));
  }

  @Override
  public Optional<BeaconBlockHeader> getBlockHeader(final DataColumnSidecar dataColumnSidecar) {
    return Optional.of(
        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader().getMessage());
  }

  @Override
  public void cacheValidatedInfo(
      final DataColumnSidecar dataColumnSidecar,
      final Set<Bytes32> validSignedBlockHeaders,
      final Set<InclusionProofInfo> validInclusionProofInfoSet) {
    final DataColumnSidecarFulu fuluSidecar = DataColumnSidecarFulu.required(dataColumnSidecar);

    // Cache signed block header hash for known valid header optimization
    validSignedBlockHeaders.add(fuluSidecar.getSignedBlockHeader().hashTreeRoot());

    // Cache inclusion proof info for future validations
    validInclusionProofInfoSet.add(
        new InclusionProofInfo(
            dataColumnSidecar.getKzgCommitments().hashTreeRoot(),
            fuluSidecar.getKzgCommitmentsInclusionProof().hashTreeRoot(),
            fuluSidecar.getBlockBodyRoot()));
  }
}
