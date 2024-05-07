/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.electra.block;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigElectra.FULL_EXIT_REQUEST_AMOUNT;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.consolidations.Consolidation;
import tech.pegasys.teku.spec.datastructures.consolidations.SignedConsolidation;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExpectedWithdrawals;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerWithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadElectra;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataValidator.AttestationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BlockProcessorElectra extends BlockProcessorDeneb {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfigElectra specConfigElectra;
  private final PredicatesElectra predicatesElectra;
  private final BeaconStateMutatorsElectra beaconStateMutatorsElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;

  public BlockProcessorElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final MiscHelpersDeneb miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsElectra schemaDefinitions) {
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
        schemaDefinitions);
    this.specConfigElectra = specConfig;
    this.predicatesElectra = PredicatesElectra.required(predicates);
    this.beaconStateMutatorsElectra = beaconStateMutators;
    this.schemaDefinitionsElectra = schemaDefinitions;
  }

  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    super.processOperationsNoValidation(state, body, indexedAttestationCache);

    safelyProcess(
        () -> {
          processDepositReceipts(
              state,
              body.getOptionalExecutionPayload()
                  .flatMap(ExecutionPayload::toVersionElectra)
                  .map(ExecutionPayloadElectra::getDepositReceipts)
                  .orElseThrow(
                      () ->
                          new BlockProcessingException(
                              "Deposit receipts were not found during block processing.")));
          processConsolidations(state, BeaconBlockBodyElectra.required(body).getConsolidations());
        });
  }

  @Override
  protected void verifyOutstandingDepositsAreProcessed(
      final BeaconState state, final BeaconBlockBody body) {
    final UInt64 eth1DepositIndexLimit =
        state
            .getEth1Data()
            .getDepositCount()
            .min(BeaconStateElectra.required(state).getDepositReceiptsStartIndex());

    if (state.getEth1DepositIndex().isLessThan(eth1DepositIndexLimit)) {
      final int expectedDepositCount =
          Math.min(
              specConfig.getMaxDeposits(),
              eth1DepositIndexLimit.minusMinZero(state.getEth1DepositIndex()).intValue());

      checkArgument(
          body.getDeposits().size() == expectedDepositCount,
          "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");
    } else {
      checkArgument(
          body.getDeposits().isEmpty(),
          "process_operations: Verify that former deposit mechanism has been disabled");
    }
  }

  @Override
  protected void processExecutionLayerWithdrawalRequests(
      final MutableBeaconState state,
      final Optional<ExecutionPayload> executionPayload,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    this.processExecutionLayerWithdrawalRequests(
        state,
        getExecutionLayerWithdrawalRequestsFromBlock(executionPayload),
        validatorExitContextSupplier);
  }

  // process_withdrawals
  @Override
  public void processWithdrawals(
      final MutableBeaconState genericState, final ExecutionPayloadSummary payloadSummary)
      throws BlockProcessingException {
    final ExpectedWithdrawals expectedWithdrawals = getExpectedWithdrawals(genericState);
    expectedWithdrawals.processWithdrawals(
        genericState,
        payloadSummary,
        schemaDefinitionsElectra,
        beaconStateMutators,
        specConfigElectra);
  }

  /**
   * Implements process_execution_layer_withdrawal_request from consensus-specs (EIP-7002 &
   * EIP-7251).
   */
  @Override
  public void processExecutionLayerWithdrawalRequests(
      final MutableBeaconState state,
      final SszList<ExecutionLayerWithdrawalRequest> withdrawalRequests,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(state.getSlot());

    withdrawalRequests.forEach(
        withdrawalRequest -> {
          // If partial withdrawal queue is full, only full exits are processed
          final boolean isFullExitRequest =
              withdrawalRequest.getAmount().equals(FULL_EXIT_REQUEST_AMOUNT);
          final boolean partialWithdrawalsQueueFull =
              state.toVersionElectra().orElseThrow().getPendingPartialWithdrawals().size()
                  >= specConfigElectra.getPendingPartialWithdrawalsLimit();
          if (partialWithdrawalsQueueFull && !isFullExitRequest) {
            return;
          }

          final Optional<Integer> maybeValidatorIndex =
              validatorsUtil.getValidatorIndex(state, withdrawalRequest.getValidatorPublicKey());
          if (maybeValidatorIndex.isEmpty()) {
            return;
          }

          final int validatorIndex = maybeValidatorIndex.get();
          final Validator validator = state.getValidators().get(validatorIndex);

          // Check if validator has an execution address set
          final boolean hasExecutionAddress =
              predicatesElectra.hasExecutionWithdrawalCredential(validator);
          if (!hasExecutionAddress) {
            return;
          }

          // Check withdrawalRequest source_address matches validator eth1 withdrawal credentials
          final Bytes20 executionAddress =
              new Bytes20(validator.getWithdrawalCredentials().slice(12));
          final boolean isCorrectSourceAddress =
              executionAddress.equals(withdrawalRequest.getSourceAddress());
          if (!isCorrectSourceAddress) {
            return;
          }

          // Check if validator is active
          final boolean isValidatorActive = predicates.isActiveValidator(validator, currentEpoch);
          if (!isValidatorActive) {
            return;
          }

          // Check if validator has already initiated exit
          final boolean hasInitiatedExit = !validator.getExitEpoch().equals(FAR_FUTURE_EPOCH);
          if (hasInitiatedExit) {
            return;
          }

          // Check if validator has been active long enough
          final boolean validatorActiveLongEnough =
              currentEpoch.isGreaterThanOrEqualTo(
                  validator.getActivationEpoch().plus(specConfig.getShardCommitteePeriod()));
          if (!validatorActiveLongEnough) {
            return;
          }

          final UInt64 pendingBalanceToWithdraw =
              validatorsUtil.getPendingBalanceToWithdraw(state, validatorIndex);
          if (isFullExitRequest) {
            // Only exit validator if it has no pending withdrawals in the queue
            if (pendingBalanceToWithdraw.isZero()) {
              beaconStateMutators.initiateValidatorExit(
                  state, validatorIndex, validatorExitContextSupplier);
              return;
            }
          }

          final UInt64 validatorBalance = state.getBalances().get(validatorIndex).get();
          final UInt64 minActivationBalance = specConfigElectra.getMinActivationBalance();

          final boolean hasCompoundingWithdrawalCredential =
              predicatesElectra.hasCompoundingWithdrawalCredential(validator);
          final boolean hasSufficientEffectiveBalance =
              validator.getEffectiveBalance().isGreaterThanOrEqualTo(minActivationBalance);
          final boolean hasExcessBalance =
              validatorBalance.isGreaterThan(minActivationBalance.plus(pendingBalanceToWithdraw));
          if (hasCompoundingWithdrawalCredential
              && hasSufficientEffectiveBalance
              && hasExcessBalance) {
            final UInt64 toWithdraw =
                validatorBalance
                    .minusMinZero(minActivationBalance)
                    .minusMinZero(pendingBalanceToWithdraw)
                    .min(withdrawalRequest.getAmount());
            final MutableBeaconStateElectra electraState =
                MutableBeaconStateElectra.required(state);
            final UInt64 exitQueueEpoch =
                beaconStateMutatorsElectra.computeExitEpochAndUpdateChurn(electraState, toWithdraw);
            final UInt64 withdrawableEpoch =
                exitQueueEpoch.plus(specConfigElectra.getMinValidatorWithdrawabilityDelay());

            electraState
                .getPendingPartialWithdrawals()
                .append(
                    schemaDefinitionsElectra
                        .getPendingPartialWithdrawalSchema()
                        .create(
                            SszUInt64.of(UInt64.fromLongBits(validatorIndex)),
                            SszUInt64.of(toWithdraw),
                            SszUInt64.of(withdrawableEpoch)));
          }
        });
  }

  private SszList<ExecutionLayerWithdrawalRequest> getExecutionLayerWithdrawalRequestsFromBlock(
      final Optional<ExecutionPayload> maybeExecutionPayload) throws BlockProcessingException {
    return maybeExecutionPayload
        .flatMap(ExecutionPayload::toVersionElectra)
        .map(ExecutionPayloadElectra::getWithdrawalRequests)
        .orElseThrow(
            () ->
                new BlockProcessingException(
                    "Execution layer withdrawal requests were not found during block processing."));
  }

  /*
   Implements process_deposit_receipt from consensus-specs (EIP-6110)
  */
  @Override
  public void processDepositReceipts(
      final MutableBeaconState state, final SszList<DepositReceipt> depositReceipts)
      throws BlockProcessingException {
    final MutableBeaconStateElectra electraState = MutableBeaconStateElectra.required(state);
    for (DepositReceipt depositReceipt : depositReceipts) {
      // process_deposit_receipt
      if (electraState
          .getDepositReceiptsStartIndex()
          .equals(SpecConfigElectra.UNSET_DEPOSIT_RECEIPTS_START_INDEX)) {
        electraState.setDepositReceiptsStartIndex(depositReceipt.getIndex());
      }
      applyDeposit(
          state,
          depositReceipt.getPubkey(),
          depositReceipt.getWithdrawalCredentials(),
          depositReceipt.getAmount(),
          depositReceipt.getSignature(),
          Optional.empty(),
          false);
    }
  }

  /*
   Implements process_consolidation from consensus-specs (EIP-7251)
  */
  @Override
  public void processConsolidations(
      final MutableBeaconState state, final SszList<SignedConsolidation> consolidations)
      throws BlockProcessingException {
    final MutableBeaconStateElectra electraState = MutableBeaconStateElectra.required(state);

    for (final SignedConsolidation signedConsolidation : consolidations) {
      // If the pending consolidations queue is full, no consolidations are allowed in the block
      assertCondition(
          electraState.getPendingConsolidations().size()
              < specConfigElectra.getPendingConsolidationsLimit(),
          "Consolidation queue is full");

      // If there is too little available consolidation churn limit, no consolidations are allowed
      // in the block
      assertCondition(
          BeaconStateAccessorsElectra.required(beaconStateAccessors)
              .getConsolidationChurnLimit(electraState)
              .isGreaterThan(specConfigElectra.getMinActivationBalance()),
          "Not enough consolidation churn limit available");

      final Consolidation consolidation = signedConsolidation.getMessage();

      // Verify that source != target, so a consolidation cannot be used as an exit.
      assertCondition(
          consolidation.getSourceIndex() != consolidation.getTargetIndex(),
          "Consolidation source_index and target_index must be different");

      final Validator sourceValidator = state.getValidators().get(consolidation.getSourceIndex());
      final Validator targetValidator = state.getValidators().get(consolidation.getTargetIndex());
      final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(electraState);

      assertCondition(
          predicatesElectra.isActiveValidator(sourceValidator, currentEpoch),
          "Source validator in consolidation is inactive");
      assertCondition(
          predicatesElectra.isActiveValidator(targetValidator, currentEpoch),
          "Target validator in consolidation is inactive");

      // Verify exits for source and target have not been initiated
      assertCondition(
          sourceValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH),
          "Source validator in consolidation is exiting");
      assertCondition(
          targetValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH),
          "Target validator in consolidation is exiting");

      // Consolidations must specify an epoch when they become valid; they are not valid before then
      assertCondition(
          currentEpoch.isGreaterThanOrEqualTo(consolidation.getEpoch()),
          "Consolidation must have an activation epoch no greater than current epoch");

      // Verify the source and the target have Execution layer withdrawal credentials
      assertCondition(
          predicatesElectra.hasExecutionWithdrawalCredential(sourceValidator),
          "Source validator in consolidation must have execution withdrawal credentials");
      assertCondition(
          predicatesElectra.hasExecutionWithdrawalCredential(targetValidator),
          "Target validator in consolidation must have execution withdrawal credentials");
      // Verify the same withdrawal address
      final Eth1Address sourceValidatorExecutionAddress =
          Predicates.getExecutionAddressUnchecked(sourceValidator.getWithdrawalCredentials());
      final Eth1Address targetValidatorExecutionAddress =
          Predicates.getExecutionAddressUnchecked(targetValidator.getWithdrawalCredentials());
      assertCondition(
          sourceValidatorExecutionAddress.equals(targetValidatorExecutionAddress),
          "Source and Target validators in consolidation must have the same withdrawal credentials");

      // Verify consolidation is signed by the source and the target
      // Note: we are not concerned about batch verifying these signatures at this point. It is
      // likely that SignedConsolidation will disappear in favour of EL-triggered consolidation
      // operations.
      assertCondition(
          operationSignatureVerifier.verifyConsolidationSignature(
              electraState, signedConsolidation, BLSSignatureVerifier.SIMPLE),
          "Invalid consolidation signature");

      // Initiate source validator exit and append pending consolidation
      final UInt64 exitEpoch =
          beaconStateMutatorsElectra.computeConsolidationEpochAndUpdateChurn(
              electraState, sourceValidator.getEffectiveBalance());
      final UInt64 withdrawableEpoch =
          exitEpoch.plus(specConfigElectra.getMinValidatorWithdrawabilityDelay());
      electraState
          .getValidators()
          .update(
              consolidation.getSourceIndex(),
              v -> v.withExitEpoch(exitEpoch).withWithdrawableEpoch(withdrawableEpoch));

      final PendingConsolidation pendingConsolidation =
          new PendingConsolidation(
              schemaDefinitionsElectra.getPendingConsolidationSchema(),
              SszUInt64.of(UInt64.valueOf(consolidation.getSourceIndex())),
              SszUInt64.of(UInt64.valueOf(consolidation.getTargetIndex())));
      electraState.getPendingConsolidations().append(pendingConsolidation);
    }
  }

  @Override
  protected void applyDepositToValidatorIndex(
      final MutableBeaconState state,
      final Bytes32 withdrawalCredentials,
      final boolean signatureAlreadyVerified,
      final int validatorIndex,
      final UInt64 amount,
      final BLSPublicKey pubkey,
      final BLSSignature signature) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    final SszMutableList<PendingBalanceDeposit> pendingBalanceDeposits =
        MutableBeaconStateElectra.required(state).getPendingBalanceDeposits();
    pendingBalanceDeposits.append(
        schemaDefinitionsElectra
            .getPendingBalanceDepositSchema()
            .create(SszUInt64.of(UInt64.fromLongBits(validatorIndex)), SszUInt64.of(amount)));
    stateElectra.setPendingBalanceDeposits(pendingBalanceDeposits);
    if (predicatesElectra.isCompoundingWithdrawalCredential(withdrawalCredentials)
        && PredicatesElectra.isEth1WithdrawalCredential(
            state.getValidators().get(validatorIndex).getWithdrawalCredentials())
        && (signatureAlreadyVerified
            || depositSignatureIsValid(pubkey, withdrawalCredentials, amount, signature))) {
      beaconStateMutatorsElectra.switchToCompoundingValidator(stateElectra, validatorIndex);
    }
  }

  @Override
  protected void addValidatorToRegistry(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount) {
    final Validator validator = getValidatorFromDeposit(pubkey, withdrawalCredentials);
    LOG.debug("Adding new validator with index {} to state", state.getValidators().size());
    state.getValidators().append(validator);
    final int validatorIndex = validatorsUtil.getValidatorIndex(state, pubkey).orElseThrow();
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);

    stateElectra.getBalances().appendElement(UInt64.ZERO);
    stateElectra.getPreviousEpochParticipation().append(SszByte.ZERO);
    stateElectra.getCurrentEpochParticipation().append(SszByte.ZERO);
    stateElectra.getInactivityScores().append(SszUInt64.ZERO);
    final SszMutableList<PendingBalanceDeposit> pendingBalanceDeposits =
        MutableBeaconStateElectra.required(state).getPendingBalanceDeposits();
    pendingBalanceDeposits.append(
        schemaDefinitionsElectra
            .getPendingBalanceDepositSchema()
            .create(SszUInt64.of(UInt64.fromLongBits(validatorIndex)), SszUInt64.of(amount)));
    stateElectra.setPendingBalanceDeposits(pendingBalanceDeposits);
  }

  @Override
  protected void assertAttestationValid(
      final MutableBeaconState state, final Attestation attestation) {
    final Optional<OperationInvalidReason> invalidReason =
        validateAttestation(state, attestation.getData());
    checkArgument(
        invalidReason.isEmpty(),
        "process_attestations: %s",
        invalidReason.map(OperationInvalidReason::describe).orElse(""));

    final List<UInt64> committeeIndices = attestation.getCommitteeIndicesRequired();
    final UInt64 committeeCountPerSlot =
        beaconStateAccessors.getCommitteeCountPerSlot(
            state, attestation.getData().getTarget().getEpoch());
    final SszBitlist aggregationBits = attestation.getAggregationBits();
    final Optional<OperationInvalidReason> committeeCheckResult =
        checkCommittees(
            committeeIndices,
            committeeCountPerSlot,
            state,
            attestation.getData().getSlot(),
            aggregationBits);
    if (committeeCheckResult.isPresent()) {
      throw new IllegalArgumentException(committeeCheckResult.get().describe());
    }
  }

  private Optional<OperationInvalidReason> checkCommittees(
      final List<UInt64> committeeIndices,
      final UInt64 committeeCountPerSlot,
      final BeaconState state,
      final UInt64 slot,
      final SszBitlist aggregationBits) {
    int participantsCount = 0;
    for (final UInt64 committeeIndex : committeeIndices) {
      if (committeeIndex.isGreaterThanOrEqualTo(committeeCountPerSlot)) {
        return Optional.of(AttestationInvalidReason.COMMITTEE_INDEX_TOO_HIGH);
      }
      final IntList committee =
          beaconStateAccessors.getBeaconCommittee(state, slot, committeeIndex);
      participantsCount += committee.size();
    }
    if (participantsCount != aggregationBits.size()) {
      return Optional.of(AttestationInvalidReason.PARTICIPANTS_COUNT_MISMATCH);
    }
    return Optional.empty();
  }

  protected Validator getValidatorFromDeposit(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials) {
    final UInt64 effectiveBalance = UInt64.ZERO;
    return new Validator(
        pubkey,
        withdrawalCredentials,
        effectiveBalance,
        false,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH);
  }
}
