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

package tech.pegasys.teku.spec.logic.versions.gloas.block;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.block.BlockProcessorFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BlockProcessorGloas extends BlockProcessorFulu {

  private final PredicatesGloas predicatesGloas;
  private final SchemaDefinitionsGloas schemaDefinitionsGloas;

  public BlockProcessorGloas(
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsGloas schemaDefinitions,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final ExecutionRequestsProcessorElectra executionRequestsProcessor) {
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
        schemaDefinitions,
        withdrawalsHelpers,
        executionRequestsDataCodec,
        executionRequestsProcessor);
    this.predicatesGloas = predicates;
    this.schemaDefinitionsGloas = schemaDefinitions;
  }

  @Override
  public void executionProcessing(
      final MutableBeaconState genericState,
      final BeaconBlock beaconBlock,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          processWithdrawals(genericState, Optional.empty());
          processExecutionPayloadBid(genericState, beaconBlock);
        });
  }

  // process_withdrawals with only state as a parameter
  @Override
  public void processWithdrawals(
      final MutableBeaconState state, final Optional<ExecutionPayloadSummary> payloadSummary)
      throws BlockProcessingException {
    withdrawalsHelpers.processWithdrawals(state);
  }

  @Override
  public void processExecutionPayloadBid(
      final MutableBeaconState state, final BeaconBlock beaconBlock)
      throws BlockProcessingException {
    final SignedExecutionPayloadBid signedBid =
        beaconBlock
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow(
                () ->
                    new BlockProcessingException(
                        "Signed Execution Payload Bid expected as part of body"));

    final ExecutionPayloadBid bid = signedBid.getMessage();

    final UInt64 builderIndex = bid.getBuilderIndex();
    final Validator builder = state.getValidators().get(builderIndex.intValue());

    final UInt64 amount = bid.getValue();

    if (builderIndex.equals(beaconBlock.getProposerIndex())) {
      // For self-builds, amount must be zero regardless of withdrawal credential prefix
      if (!amount.isZero()) {
        throw new BlockProcessingException("Amount must be zero for self-build blocks");
      }
      if (!signedBid.getSignature().isInfinity()) {
        throw new BlockProcessingException(
            "Signature must be G2_POINT_AT_INFINITY for self-builds");
      }
    } else {
      // Non-self builds require builder withdrawal credential
      if (!predicatesGloas.hasBuilderWithdrawalCredential(builder)) {
        throw new BlockProcessingException("Non-self builds require builder withdrawal credential");
      }
      if (!operationSignatureVerifier.verifyExecutionPayloadBidSignature(
          state, signedBid, BLSSignatureVerifier.SIMPLE)) {
        throw new BlockProcessingException("Signature for the signed bind was invalid");
      }
    }

    // Check that the builder is active, non-slashed
    if (!predicatesGloas.isActiveValidator(
        builder, miscHelpers.computeEpochAtSlot(state.getSlot()))) {
      throw new BlockProcessingException("Builder is not an active validator");
    }
    if (builder.isSlashed()) {
      throw new BlockProcessingException("Builder is slashed");
    }

    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);

    // Check that the builder has funds to cover the bid
    final UInt64 pendingPayments =
        stateGloas.getBuilderPendingPayments().stream()
            .filter(payment -> payment.getWithdrawal().getBuilderIndex().equals(builderIndex))
            .map(payment -> payment.getWithdrawal().getAmount())
            .reduce(UInt64.ZERO, UInt64::plus);

    final UInt64 pendingWithdrawals =
        stateGloas.getBuilderPendingWithdrawals().stream()
            .filter(withdrawal -> withdrawal.getBuilderIndex().equals(builderIndex))
            .map(BuilderPendingWithdrawal::getAmount)
            .reduce(UInt64.ZERO, UInt64::plus);

    final UInt64 builderBalance = state.getBalances().get(builderIndex.intValue()).get();

    if (!amount.isZero()
        && !builderBalance.isGreaterThanOrEqualTo(
            amount
                .plus(pendingPayments)
                .plus(pendingWithdrawals)
                .plus(SpecConfigElectra.required(specConfig).getMinActivationBalance()))) {
      throw new BlockProcessingException("Builder doesn't have funds to cover the bid");
    }

    // Verify that the bid is for the current slot
    if (!bid.getSlot().equals(beaconBlock.getSlot())) {
      throw new BlockProcessingException("Bid is not for the current slot");
    }

    // Verify that the bid is for the right parent block
    if (!bid.getParentBlockHash().equals(stateGloas.getLatestBlockHash())
        || !bid.getParentBlockRoot().equals(beaconBlock.getParentRoot())) {
      throw new BlockProcessingException("Bid is not for the right parent block");
    }

    // Record the pending payment
    final BuilderPendingPayment pendingPayment =
        schemaDefinitionsGloas
            .getBuilderPendingPaymentSchema()
            .create(
                UInt64.ZERO,
                schemaDefinitionsGloas
                    .getBuilderPendingWithdrawalSchema()
                    .create(bid.getFeeRecipient(), amount, builderIndex, UInt64.ZERO));

    stateGloas
        .getBuilderPendingPayments()
        .set(
            bid.getSlot()
                .mod(specConfig.getSlotsPerEpoch())
                .plus(specConfig.getSlotsPerEpoch())
                .intValue(),
            pendingPayment);

    // Cache the execution payload bid
    stateGloas.setLatestExecutionPayloadBid(bid);
  }

  @Override
  public void processExecutionPayload(
      final MutableBeaconState genericState,
      final BeaconBlockBody beaconBlockBody,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor) {
    throw new UnsupportedOperationException("process_execution_payload has been removed in Gloas");
  }

  @Override
  public void processExecutionRequests(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier) {
    // Execution requests are removed from the BeaconBlockBody in Gloas and are instead processed as
    // part of process_execution_payload
  }
}
