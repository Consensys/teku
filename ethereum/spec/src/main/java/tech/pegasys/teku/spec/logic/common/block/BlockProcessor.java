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

package tech.pegasys.teku.spec.logic.common.block;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.deneb.block.KzgCommitmentsProcessor;

public interface BlockProcessor {
  Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data);

  BeaconState processAndValidateBlock(
      SignedBeaconBlock signedBlock,
      BeaconState blockSlotState,
      IndexedAttestationCache indexedAttestationCache,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor,
      KzgCommitmentsProcessor kzgCommitmentsProcessor,
      BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker)
      throws StateTransitionException;

  /**
   * Processes the given block on top of {@code blockSlotState} and optionally validates the block
   *
   * @param signedBlock The block to be processed
   * @param blockSlotState The preState on which this block should be processed, this preState must
   *     already be advanced to the block's slot
   * @param indexedAttestationCache A cache of indexed attestations
   * @param signatureVerifier The signature verifier to use
   * @param payloadExecutor the optimistic payload executor to begin execution with
   * @return The post state after processing the block on top of {@code blockSlotState}
   * @throws StateTransitionException If the block is invalid or cannot be processed
   */
  BeaconState processAndValidateBlock(
      SignedBeaconBlock signedBlock,
      BeaconState blockSlotState,
      IndexedAttestationCache indexedAttestationCache,
      BLSSignatureVerifier signatureVerifier,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor,
      KzgCommitmentsProcessor kzgCommitmentsProcessor,
      BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker)
      throws StateTransitionException;

  BeaconState processUnsignedBlock(
      BeaconState preState,
      BeaconBlock block,
      IndexedAttestationCache indexedAttestationCache,
      BLSSignatureVerifier signatureVerifier,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor,
      KzgCommitmentsProcessor kzgCommitmentsProcessor,
      BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker)
      throws BlockProcessingException;

  void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException;

  boolean isEnoughVotesToUpdateEth1Data(long voteCount);

  long getVoteCount(BeaconState state, Eth1Data eth1Data);

  void processProposerSlashings(
      MutableBeaconState state,
      SszList<ProposerSlashing> proposerSlashings,
      BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException;

  void processAttesterSlashings(
      MutableBeaconState state, SszList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException;

  void processAttesterSlashings(
      MutableBeaconState state,
      SszList<AttesterSlashing> attesterSlashings,
      Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException;

  void processAttestations(
      MutableBeaconState state,
      SszList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException;

  void processDeposits(MutableBeaconState state, SszList<? extends Deposit> deposits)
      throws BlockProcessingException;

  void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Object2IntMap<BLSPublicKey> pubKeyToIndexMap,
      final boolean signatureCheckIsDone);

  void processVoluntaryExits(
      MutableBeaconState state,
      SszList<SignedVoluntaryExit> exits,
      BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException;

  void processSyncAggregate(
      MutableBeaconState state, SyncAggregate syncAggregate, BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException;

  UInt64 computeParticipantReward(final BeaconStateAltair state);

  void processExecutionPayload(
      MutableBeaconState state,
      ExecutionPayloadHeader executionPayloadHeader,
      Optional<ExecutionPayload> executionPayload,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException;

  void validateExecutionPayload(
      BeaconState state,
      ExecutionPayloadHeader executionPayloadHeader,
      Optional<ExecutionPayload> executionPayload,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException;

  boolean isOptimistic();

  void processBlsToExecutionChanges(
      MutableBeaconState state, SszList<SignedBlsToExecutionChange> blsToExecutionChanges)
      throws BlockProcessingException;

  void processWithdrawals(MutableBeaconState state, ExecutionPayloadSummary payloadSummary)
      throws BlockProcessingException;

  Optional<List<Withdrawal>> getExpectedWithdrawals(BeaconState preState);

  void processBlobKzgCommitments(
      MutableBeaconState state,
      BeaconBlockBody body,
      KzgCommitmentsProcessor kzgCommitmentsProcessor)
      throws BlockProcessingException;
}
