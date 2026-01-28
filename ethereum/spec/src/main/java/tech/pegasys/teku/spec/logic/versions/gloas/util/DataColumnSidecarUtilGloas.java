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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
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

  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotTimingValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFromFuture) {
    return Optional.empty();
  }

  @Override
  public Optional<SlotInclusionGossipValidationResult> performSlotFinalizationValidation(
      final DataColumnSidecar dataColumnSidecar, final Predicate<UInt64> isSlotFinalized) {
    return Optional.empty();
  }

  /*
   * [IGNORE] The sidecar's beacon_block_root has been seen via a valid signed execution payload bid.
   * A client MAY queue the sidecar for processing once the block is retrieved.
   */
  @Override
  public boolean isBlockSeen(
      final DataColumnSidecar dataColumnSidecar, final Function<Bytes32, Boolean> isBlockRootSeen) {
    return isBlockRootSeen.apply(dataColumnSidecar.getBeaconBlockRoot());
  }

  /*
   * [REJECT] The hash of the sidecar's kzg_commitments matches the blob_kzg_commitments_root
   * in the corresponding builder's bid for sidecar.beacon_block_root
   */
  @Override
  public DataColumnSidecarValidationResult validateKzgCommitmentsRoot(
      final DataColumnSidecar dataColumnSidecar,
      final Function<Bytes32, Optional<Bytes32>> getBlockKzgCommitmentsRoot) {

    final Bytes32 beaconBlockRoot = dataColumnSidecar.getBeaconBlockRoot();
    final Optional<Bytes32> maybeBlockKzgCommitmentsRoot =
        getBlockKzgCommitmentsRoot.apply(beaconBlockRoot);
    if (maybeBlockKzgCommitmentsRoot.isEmpty()) {
      return DataColumnSidecarValidationResult.invalid(
          "DataColumnSidecar's beacon_block_root does not correspond to a known execution payload bid");
    }

    final Bytes32 commitmentsRoot = maybeBlockKzgCommitmentsRoot.get();
    final Bytes32 dataColumnSidecarCommitmentsRoot =
        dataColumnSidecar.getKzgCommitments().hashTreeRoot();
    if (!commitmentsRoot.equals(dataColumnSidecarCommitmentsRoot)) {
      return DataColumnSidecarValidationResult.invalid(
          () ->
              String.format(
                  "DataColumnSidecar's kzg_commitments root %s does not match the bid's blob_kzg_commitments_root %s",
                  dataColumnSidecarCommitmentsRoot, commitmentsRoot));
    }

    return DataColumnSidecarValidationResult.valid();
  }

  /*
   * [REJECT] The sidecar's slot matches the slot of the block with root beacon_block_root
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

  @Override
  public DataColumnSidecarValidationResult validateParentBlock(
      final BeaconBlockHeader dataColumnSidecar,
      final Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock) {
    // Gloas does not validate parent block (no header in Gloas sidecars)
    return DataColumnSidecarValidationResult.valid();
  }

  @Override
  public DataColumnSidecarTrackingKey extractTrackingKeyFromHeader(
      final BeaconBlockHeader header, final DataColumnSidecar dataColumnSidecar) {
    return extractTrackingKey(dataColumnSidecar);
  }

  @Override
  public DataColumnSidecarTrackingKey extractTrackingKey(
      final DataColumnSidecar dataColumnSidecar) {
    return new GloasTrackingKey(
        dataColumnSidecar.getBeaconBlockRoot(), dataColumnSidecar.getIndex());
  }

  @Override
  public boolean verifyDataColumnSidecarStructure(
      final SpecLogic specLogic, final DataColumnSidecar dataColumnSidecar) {
    final MiscHelpersGloas miscHelpersGloas = MiscHelpersGloas.required(specLogic.miscHelpers());
    return miscHelpersGloas.verifyDataColumnSidecar(dataColumnSidecar);
  }

  @Override
  public boolean verifyInclusionProof(
      final SpecLogic specLogic,
      final DataColumnSidecar dataColumnSidecar,
      final Set<InclusionProofInfo> validInclusionProofInfoSet) {
    // Gloas doesn't have inclusion proof requirement (no header in Gloas sidecars)
    return true;
  }

  /*
   * [REJECT] The sidecar's column data is valid as verified by
   *   verify_data_column_sidecar_kzg_proofs(sidecar)
   */
  @Override
  public boolean verifyDataColumnSidecarKzgProofs(
      final SpecLogic specLogic, final DataColumnSidecar dataColumnSidecar) {
    final MiscHelpersGloas miscHelpersGloas = MiscHelpersGloas.required(specLogic.miscHelpers());
    return miscHelpersGloas.verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
  }

  @Override
  public Optional<SignatureVerificationData> getSignatureVerificationData(
      final Spec spec, final BeaconState state, final DataColumnSidecar dataColumnSidecar) {
    // Gloas doesn't have header signature requirement
    return Optional.empty();
  }

  @Override
  public Optional<BeaconBlockHeader> getBlockHeader(final DataColumnSidecar dataColumnSidecar) {
    // Gloas sidecars don't contain block headers
    return Optional.empty();
  }

  @Override
  public void cacheValidatedInfo(
      final DataColumnSidecar dataColumnSidecar,
      final Set<Bytes32> validSignedBlockHeaders,
      final Set<InclusionProofInfo> validInclusionProofInfoSet) {
    // Nothing to cache for Gloas (no header, no inclusion proof)
  }
}
