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
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

/**
 * Fork-aware utility for Data Column Sidecar gossip validation. This interface defines the contract
 * for fork-specific validation logic.
 *
 * <p>Different fork implementations provide fork-specific behavior. Methods that don't apply to a
 * particular fork return {@link DataColumnSidecarValidationResult#VALID}.
 */
public interface DataColumnSidecarUtil {

  Optional<SlotInclusionGossipValidationResult> performSlotTimingValidation(
      DataColumnSidecar dataColumnSidecar, Predicate<UInt64> isSlotFromFuture);

  Optional<SlotInclusionGossipValidationResult> performSlotFinalizationValidation(
      DataColumnSidecar dataColumnSidecar, Predicate<UInt64> isSlotFinalized);

  boolean isBlockParentSeen(
      DataColumnSidecar dataColumnSidecar, Function<Bytes32, Boolean> isBlockRootSeen);

  boolean isBlockWithBidSeen(
      DataColumnSidecar dataColumnSidecar, Function<Bytes32, Boolean> isBlockRootSeen);

  DataColumnSidecarValidationResult validateKzgCommitmentsRoot(
      DataColumnSidecar dataColumnSidecar,
      Function<Bytes32, Optional<BeaconBlock>> getBeaconBlockByRoot);

  DataColumnSidecarValidationResult validateBlockSlot(
      DataColumnSidecar dataColumnSidecar, Function<Bytes32, Optional<UInt64>> getSlotForBlockRoot);

  DataColumnSidecarValidationResult validateParentBlock(
      BeaconBlockHeader blockHeader,
      UInt64 parentBlockSlot,
      Map<Bytes32, BlockImportResult> invalidBlockRoots,
      BiPredicate<UInt64, Bytes32> currentFinalizedCheckpointIsAncestorOfBlock);

  DataColumnSidecarTrackingKey extractTrackingKey(DataColumnSidecar dataColumnSidecar);

  DataColumnSidecarTrackingKey extractTrackingKeyFromHeader(
      BeaconBlockHeader header, DataColumnSidecar dataColumnSidecar);

  boolean verifyDataColumnSidecarStructure(
      SpecLogic specLogic, DataColumnSidecar dataColumnSidecar);

  boolean verifyInclusionProof(
      SpecLogic specLogic,
      DataColumnSidecar dataColumnSidecar,
      Set<InclusionProofInfo> validInclusionProofInfoSet);

  boolean verifyDataColumnSidecarKzgProofs(
      SpecLogic specLogic, DataColumnSidecar dataColumnSidecar);

  Optional<SignatureVerificationData> getSignatureVerificationData(
      Spec spec, BeaconState state, DataColumnSidecar dataColumnSidecar);

  Optional<BeaconBlockHeader> getBlockHeader(DataColumnSidecar dataColumnSidecar);

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
