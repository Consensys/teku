/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.eip4844.block;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapella;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.eip4844.helpers.MiscHelpersEip4844;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip4844;

public class BlockProcessorEip4844 extends BlockProcessorCapella {

  public BlockProcessorEip4844(
      final SpecConfigEip4844 specConfig,
      final Predicates predicates,
      final MiscHelpersEip4844 miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsEip4844 schemaDefinitions) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        syncCommitteeUtil,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator,
        SchemaDefinitionsCapella.required(schemaDefinitions));
  }

  @Override
  public void processBlock(
      final MutableBeaconState genericState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor,
      final KzgCommitmentsProcessor kzgCommitmentsProcessor,
      final BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker)
      throws BlockProcessingException {
    super.processBlock(
        genericState,
        block,
        indexedAttestationCache,
        signatureVerifier,
        payloadExecutor,
        kzgCommitmentsProcessor,
        blobsSidecarAvailabilityChecker);

    processBlobKzgCommitments(genericState, block.getBody(), kzgCommitmentsProcessor);

    final boolean blobsRetrievalStarted =
        blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck();
    if (!blobsRetrievalStarted) {
      throw new BlockProcessingException(
          "Blobs Sidecar availability check initiation has been rejected");
    }
  }

  /**
   * See <a
   * href=https://github.com/ethereum/consensus-specs/blob/4be8f7d669d785a184811776b3ff5ca18ae21e73/specs/eip4844/beacon-chain.md#disabling-withdrawals>Disabling
   * Withdrawals</a>
   */
  @Override
  public void processBlsToExecutionChanges(
      MutableBeaconState state, SszList<SignedBlsToExecutionChange> blsToExecutionChanges)
      throws BlockProcessingException {
    // NOOP
  }

  /**
   * See <a
   * href=https://github.com/ethereum/consensus-specs/blob/4be8f7d669d785a184811776b3ff5ca18ae21e73/specs/eip4844/beacon-chain.md#disabling-withdrawals>Disabling
   * Withdrawals</a>
   */
  @Override
  public void processBlsToExecutionChangesNoValidation(
      MutableBeaconStateCapella state,
      SszList<SignedBlsToExecutionChange> signedBlsToExecutionChanges) {
    // NOOP
  }

  /**
   * See <a
   * href=https://github.com/ethereum/consensus-specs/blob/4be8f7d669d785a184811776b3ff5ca18ae21e73/specs/eip4844/beacon-chain.md#disabling-withdrawals>Disabling
   * Withdrawals</a>
   */
  @Override
  public void processWithdrawals(
      MutableBeaconState genericState, ExecutionPayloadSummary payloadSummary)
      throws BlockProcessingException {
    // NOOP
  }

  /**
   * See <a
   * href=https://github.com/ethereum/consensus-specs/blob/4be8f7d669d785a184811776b3ff5ca18ae21e73/specs/eip4844/beacon-chain.md#disabling-withdrawals>Disabling
   * Withdrawals</a>
   */
  @Override
  public Optional<List<Withdrawal>> getExpectedWithdrawals(BeaconState preState) {
    // NOOP
    return Optional.empty();
  }

  @Override
  public void processBlobKzgCommitments(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final KzgCommitmentsProcessor kzgCommitmentsProcessor)
      throws BlockProcessingException {
    kzgCommitmentsProcessor.processBlobKzgCommitments(body);
  }
}
