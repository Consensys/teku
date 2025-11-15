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
import java.util.OptionalInt;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
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
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BlockProcessorGloas extends BlockProcessorFulu {

  private final PredicatesGloas predicatesGloas;
  private final SchemaDefinitionsGloas schemaDefinitionsGloas;
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;
  private final AttestationUtilGloas attestationUtilGloas;

  public BlockProcessorGloas(
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtilGloas attestationUtil,
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
    this.beaconStateAccessorsGloas = beaconStateAccessors;
    this.attestationUtilGloas = attestationUtil;
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

  // process_execution_payload_bid
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
    if (!bid.getPrevRandao()
        .equals(
            beaconStateAccessors.getRandaoMix(
                state, beaconStateAccessors.getCurrentEpoch(state)))) {
      throw new BlockProcessingException("Prev randao of the bid is not as expected");
    }

    // Record the pending payment if there is some payment
    if (amount.isGreaterThan(UInt64.ZERO)) {
      final BuilderPendingPayment pendingPayment =
          schemaDefinitionsGloas
              .getBuilderPendingPaymentSchema()
              .create(
                  UInt64.ZERO,
                  schemaDefinitionsGloas
                      .getBuilderPendingWithdrawalSchema()
                      .create(
                          bid.getFeeRecipient(),
                          amount,
                          builderIndex,
                          SpecConfig.FAR_FUTURE_EPOCH));

      stateGloas
          .getBuilderPendingPayments()
          .set(
              bid.getSlot()
                  .mod(specConfig.getSlotsPerEpoch())
                  .plus(specConfig.getSlotsPerEpoch())
                  .intValue(),
              pendingPayment);
    }

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

  // Remove the BuilderPendingPayment corresponding to this proposal if it is still in the 2-epoch
  // window.
  @Override
  protected void removeBuilderPendingPayment(
      final ProposerSlashing proposerSlashing, final MutableBeaconState state) {
    final UInt64 slot = proposerSlashing.getHeader1().getMessage().getSlot();
    final UInt64 proposalEpoch = miscHelpers.computeEpochAtSlot(slot);
    OptionalInt paymentIndex = OptionalInt.empty();
    if (proposalEpoch.equals(beaconStateAccessors.getCurrentEpoch(state))) {
      paymentIndex =
          OptionalInt.of(
              specConfig.getSlotsPerEpoch() + slot.mod(specConfig.getSlotsPerEpoch()).intValue());
    } else if (proposalEpoch.equals(beaconStateAccessors.getPreviousEpoch(state))) {
      paymentIndex = OptionalInt.of(slot.mod(specConfig.getSlotsPerEpoch()).intValue());
    }
    paymentIndex.ifPresent(
        index ->
            MutableBeaconStateGloas.required(state)
                .getBuilderPendingPayments()
                .set(index, schemaDefinitionsGloas.getBuilderPendingPaymentSchema().getDefault()));
  }

  @Override
  protected int getBuilderPaymentIndex(
      final boolean currentEpochTarget, final AttestationData data) {
    if (currentEpochTarget) {
      return specConfig.getSlotsPerEpoch()
          + data.getSlot().mod(specConfig.getSlotsPerEpoch()).intValue();
    } else {
      return data.getSlot().mod(specConfig.getSlotsPerEpoch()).intValue();
    }
  }

  // Add weight for same-slot attestations when any new flag is set.
  // This ensures each validator contributes exactly once per slot.
  @Override
  protected UInt64 updateBuilderPaymentWeight(
      final int builderPaymentIndex,
      final UInt64 builderPaymentWeightDelta,
      final AttestationData data,
      final int attestingIndex,
      final BeaconState state) {
    final BuilderPendingPayment payment =
        BeaconStateGloas.required(state).getBuilderPendingPayments().get(builderPaymentIndex);
    if (beaconStateAccessorsGloas.isAttestationSameSlot(state, data)
        // only add to the payment quorum if the payment is not trivial
        && payment.getWithdrawal().getAmount().isGreaterThan(UInt64.ZERO)) {
      return builderPaymentWeightDelta.plus(
          state.getValidators().get(attestingIndex).getEffectiveBalance());
    } else {
      return builderPaymentWeightDelta;
    }
  }

  @Override
  protected void consumeAttestationProcessingResult(
      final AttestationData data,
      final AttestationProcessingResult result,
      final MutableBeaconState state) {
    super.consumeAttestationProcessingResult(data, result, state);
    // update builder payment weight
    final UInt64 weightDelta = result.builderPaymentWeightDelta();
    if (!weightDelta.isZero()) {
      final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
      final BuilderPendingPayment payment =
          stateGloas.getBuilderPendingPayments().get(result.builderPaymentIndex());
      stateGloas
          .getBuilderPendingPayments()
          .set(
              result.builderPaymentIndex(),
              payment.copyWithNewWeight(payment.getWeight().plus(weightDelta)));
    }
  }

  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache,
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    super.processOperationsNoValidation(
        state, body, indexedAttestationCache, validatorExitContextSupplier);

    safelyProcess(
        () ->
            processPayloadAttestations(
                state,
                body.getOptionalPayloadAttestations()
                    .orElseThrow(
                        () ->
                            new BlockProcessingException(
                                "Payload attestations expected as part of the body"))));
  }

  @Override
  public void processExecutionRequests(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier) {
    // Execution requests are removed from the BeaconBlockBody in Gloas and are instead processed as
    // part of process_execution_payload
  }

  @Override
  public void processPayloadAttestations(
      final MutableBeaconState state, final SszList<PayloadAttestation> payloadAttestations)
      throws BlockProcessingException {
    // process_payload_attestation
    for (final PayloadAttestation payloadAttestation : payloadAttestations) {
      final PayloadAttestationData data = payloadAttestation.getData();
      // Check that the attestation is for the parent beacon block
      if (!data.getBeaconBlockRoot().equals(state.getLatestBlockHeader().getParentRoot())) {
        throw new BlockProcessingException("Attestation is NOT for the parent beacon block");
      }
      // Check that the attestation is for the previous slot
      if (!data.getSlot().increment().equals(state.getSlot())) {
        throw new BlockProcessingException("Attestation is NOT for the previous slot");
      }
      // Verify signature
      final IndexedPayloadAttestation indexedPayloadAttestation =
          beaconStateAccessorsGloas.getIndexedPayloadAttestation(
              state, data.getSlot(), payloadAttestation);

      if (!attestationUtilGloas.isValidIndexedPayloadAttestation(
          state, indexedPayloadAttestation)) {
        throw new BlockProcessingException("Indexed payload attestation is NOT valid");
      }
    }
  }
}
