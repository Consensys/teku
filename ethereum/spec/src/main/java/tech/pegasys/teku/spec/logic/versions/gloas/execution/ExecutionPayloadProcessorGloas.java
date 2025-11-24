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

package tech.pegasys.teku.spec.logic.versions.gloas.execution;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.execution.AbstractExecutionPayloadProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.ExecutionPayloadProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.executionpayloadvalidator.ExecutionPayloadValidationResult;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionPayloadProcessorGloas extends AbstractExecutionPayloadProcessor {

  private final SpecConfigGloas specConfig;
  private final SchemaDefinitionsGloas schemaDefinitions;
  private final MiscHelpersGloas miscHelpers;
  private final BeaconStateAccessorsGloas beaconStateAccessors;
  private final BeaconStateMutatorsElectra beaconStateMutators;
  private final ExecutionRequestsDataCodec executionRequestsDataCodec;
  private final ExecutionRequestsProcessorElectra executionRequestsProcessor;

  public ExecutionPayloadProcessorGloas(
      final SpecConfigGloas specConfig,
      final SchemaDefinitionsGloas schemaDefinitions,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final ExecutionRequestsProcessorElectra executionRequestsProcessor) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateMutators = beaconStateMutators;
    this.executionRequestsDataCodec = executionRequestsDataCodec;
    this.executionRequestsProcessor = executionRequestsProcessor;
  }

  @Override
  protected ExecutionPayloadValidationResult validateExecutionPayloadPreProcessing(
      final BeaconState preState,
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final BLSSignatureVerifier signatureVerifier) {
    if (!verifyExecutionPayloadEnvelopeSignature(preState, signedEnvelope, signatureVerifier)) {
      return ExecutionPayloadValidationResult.failed(
          "Invalid execution payload signature: " + signedEnvelope);
    }
    return ExecutionPayloadValidationResult.SUCCESSFUL;
  }

  private boolean verifyExecutionPayloadEnvelopeSignature(
      final BeaconState preState,
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final BLSSignatureVerifier signatureVerifier) {
    final Validator builder =
        preState.getValidators().get(signedEnvelope.getMessage().getBuilderIndex().intValue());
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            preState.getForkInfo(),
            Domain.BEACON_BUILDER,
            miscHelpers.computeEpochAtSlot(preState.getSlot()));
    final Bytes signingRoot = miscHelpers.computeSigningRoot(signedEnvelope.getMessage(), domain);
    return signatureVerifier.verify(
        builder.getPublicKey(), signingRoot, signedEnvelope.getSignature());
  }

  @Override
  public void processUnsignedExecutionPayload(
      final MutableBeaconState state,
      final ExecutionPayloadEnvelope envelope,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws ExecutionPayloadProcessingException {
    final ExecutionPayload payload = envelope.getPayload();
    final Bytes32 previousStateRoot = state.hashTreeRoot();
    // Cache latest block header state root
    if (state.getLatestBlockHeader().getStateRoot().equals(Bytes32.ZERO)) {
      final BeaconBlockHeader latestBlockHeaderNew =
          new BeaconBlockHeader(
              state.getLatestBlockHeader().getSlot(),
              state.getLatestBlockHeader().getProposerIndex(),
              state.getLatestBlockHeader().getParentRoot(),
              previousStateRoot,
              state.getLatestBlockHeader().getBodyRoot());
      state.setLatestBlockHeader(latestBlockHeaderNew);
    }
    // Verify consistency with the beacon block
    if (!envelope.getBeaconBlockRoot().equals(state.getLatestBlockHeader().hashTreeRoot())) {
      throw new ExecutionPayloadProcessingException(
          "Envelope beacon block root is not consistent with the latest beacon block from the state");
    }
    if (!envelope.getSlot().equals(state.getSlot())) {
      throw new ExecutionPayloadProcessingException(
          "Envelope slot is not consistent with the state slot");
    }
    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);

    // Verify consistency with the committed bid
    final ExecutionPayloadBid committedBid = stateGloas.getLatestExecutionPayloadBid();

    if (!envelope.getBuilderIndex().equals(committedBid.getBuilderIndex())) {
      throw new ExecutionPayloadProcessingException(
          "Builder index of the envelope is not consistent with the builder index of the committed bid");
    }
    if (!envelope
        .getBlobKzgCommitments()
        .hashTreeRoot()
        .equals(committedBid.getBlobKzgCommitmentsRoot())) {
      throw new ExecutionPayloadProcessingException(
          "The hash tree root of the blob kzg commitments in the envelope are not consistent with the blob kzg commitments root of the committed bid");
    }
    if (!envelope.getPayload().getPrevRandao().equals(committedBid.getPrevRandao())) {
      throw new ExecutionPayloadProcessingException(
          "Prev randao of the envelope is not consistent with the prev randao of the committed bid");
    }
    // Verify the withdrawals root
    if (!ExecutionPayloadCapella.required(payload)
        .getWithdrawals()
        .hashTreeRoot()
        .equals(stateGloas.getLatestWithdrawalsRoot())) {
      throw new ExecutionPayloadProcessingException(
          "Withdrawals root of the envelope is not consistent with the latest withdrawals root in the state");
    }
    // Verify the gas_limit
    if (!committedBid.getGasLimit().equals(payload.getGasLimit())) {
      throw new ExecutionPayloadProcessingException(
          "Gas limit of the committed bid is not consistent with the gas limit of the payload");
    }
    // Verify the block hash
    if (!committedBid.getBlockHash().equals(payload.getBlockHash())) {
      throw new ExecutionPayloadProcessingException(
          "Block hash of the committed bid is not consistent with the block hash of the payload");
    }
    // Verify consistency of the parent hash with respect to the previous execution payload
    if (!payload.getParentHash().equals(stateGloas.getLatestBlockHash())) {
      throw new ExecutionPayloadProcessingException(
          "Parent hash of the payload is not consistent with the previous execution payload");
    }
    // Verify timestamp
    if (!payload
        .getTimestamp()
        .equals(miscHelpers.computeTimeAtSlot(state.getGenesisTime(), state.getSlot()))) {
      throw new ExecutionPayloadProcessingException("Timestamp of the payload is not as expected");
    }
    // Verify commitments are under limit
    if (envelope.getBlobKzgCommitments().size()
        > miscHelpers
            .getBlobParameters(beaconStateAccessors.getCurrentEpoch(state))
            .maxBlobsPerBlock()) {
      throw new ExecutionPayloadProcessingException(
          "Number of kzg commitments in the envelope exceeds max blobs per block");
    }
    // Verify the execution payload is valid
    if (payloadExecutor.isPresent()) {
      final NewPayloadRequest payloadToExecute = computeNewPayloadRequest(state, envelope);
      final boolean optimisticallyAccept =
          payloadExecutor.get().optimisticallyExecute(Optional.empty(), payloadToExecute);
      if (!optimisticallyAccept) {
        throw new ExecutionPayloadProcessingException(
            "Execution payload was not optimistically accepted");
      }
    }

    processOperations(
        state, envelope, beaconStateMutators.createValidatorExitContextSupplier(state));

    // Queue the builder payment
    final int paymentIndex =
        specConfig.getSlotsPerEpoch()
            + state.getSlot().mod(specConfig.getSlotsPerEpoch()).intValue();
    final BuilderPendingPayment payment = stateGloas.getBuilderPendingPayments().get(paymentIndex);
    final UInt64 amount = payment.getWithdrawal().getAmount();
    if (amount.isGreaterThan(0)) {
      final UInt64 exitQueueEpoch =
          beaconStateMutators.computeExitEpochAndUpdateChurn(stateGloas, amount);
      final BuilderPendingWithdrawal withdrawalToQueue =
          payment
              .getWithdrawal()
              .copyWithNewWithdrawableEpoch(
                  exitQueueEpoch.plus(specConfig.getMinValidatorWithdrawabilityDelay()));
      stateGloas.getBuilderPendingWithdrawals().append(withdrawalToQueue);
    }
    stateGloas
        .getBuilderPendingPayments()
        // BuilderPendingPayment()
        .set(paymentIndex, schemaDefinitions.getBuilderPendingPaymentSchema().getDefault());

    // Cache the execution payload hash
    final BitSet newExecutionPayloadAvailability =
        stateGloas.getExecutionPayloadAvailability().getAsBitSet();
    final int indexToModify =
        state.getSlot().mod(specConfig.getSlotsPerHistoricalRoot()).intValue();
    newExecutionPayloadAvailability.set(indexToModify, true);
    stateGloas.setExecutionPayloadAvailability(
        schemaDefinitions
            .getExecutionPayloadAvailabilitySchema()
            .wrapBitSet(
                stateGloas.getExecutionPayloadAvailability().size(),
                newExecutionPayloadAvailability));
    stateGloas.setLatestBlockHash(payload.getBlockHash());
  }

  @Override
  protected ExecutionPayloadValidationResult validateExecutionPayloadPostProcessing(
      final BeaconState postState, final ExecutionPayloadEnvelope envelope) {
    if (!postState.hashTreeRoot().equals(envelope.getStateRoot())) {
      return ExecutionPayloadValidationResult.failed(
          "Envelope state root does NOT match the calculated state root!\n"
              + "Envelope state root: "
              + envelope.getStateRoot().toHexString()
              + "\n  New state root: "
              + postState.hashTreeRoot().toHexString()
              + "\n block root: "
              + envelope.getBeaconBlockRoot());
    } else {
      return ExecutionPayloadValidationResult.SUCCESSFUL;
    }
  }

  protected NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state, final ExecutionPayloadEnvelope envelope) {
    final List<VersionedHash> versionedHashes =
        envelope.getBlobKzgCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .toList();
    final Bytes32 parentBeaconBlockRoot = state.getLatestBlockHeader().getParentRoot();
    return new NewPayloadRequest(
        envelope.getPayload(),
        versionedHashes,
        parentBeaconBlockRoot,
        executionRequestsDataCodec.encode(envelope.getExecutionRequests()));
  }

  protected void processOperations(
      final MutableBeaconState state,
      final ExecutionPayloadEnvelope envelope,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    processExecutionRequests(state, envelope, validatorExitContextSupplier);
  }

  protected void processExecutionRequests(
      final MutableBeaconState state,
      final ExecutionPayloadEnvelope envelope,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final ExecutionRequests executionRequests = envelope.getExecutionRequests();

    executionRequestsProcessor.processDepositRequests(state, executionRequests.getDeposits());
    executionRequestsProcessor.processWithdrawalRequests(
        state, executionRequests.getWithdrawals(), validatorExitContextSupplier);
    executionRequestsProcessor.processConsolidationRequests(
        state, executionRequests.getConsolidations());
  }
}
