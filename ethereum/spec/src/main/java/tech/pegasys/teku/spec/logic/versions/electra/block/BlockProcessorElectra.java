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
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExpectedWithdrawals;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
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
          final ExecutionRequests executionRequests =
              BeaconBlockBodyElectra.required(body).getExecutionRequests();
          this.processDepositRequests(state, executionRequests.getDeposits());
          this.processConsolidationRequests(state, executionRequests.getConsolidations());
        });
  }

  @Override
  protected void verifyOutstandingDepositsAreProcessed(
      final BeaconState state, final BeaconBlockBody body) {
    final UInt64 eth1DepositIndexLimit =
        state
            .getEth1Data()
            .getDepositCount()
            .min(BeaconStateElectra.required(state).getDepositRequestsStartIndex());

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
  protected void processWithdrawalRequests(
      final MutableBeaconState state,
      final BeaconBlockBody beaconBlockBody,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {

    this.processWithdrawalRequests(
        state,
        BeaconBlockBodyElectra.required(beaconBlockBody).getExecutionRequests().getWithdrawals(),
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

  /** Implements process_withdrawal_request from consensus-specs (EIP-7002 & EIP-7251). */
  @Override
  public void processWithdrawalRequests(
      final MutableBeaconState state,
      final List<WithdrawalRequest> withdrawalRequests,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final UInt64 slot = state.getSlot();
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(slot);

    LOG.debug(
        "process_withdrawal_request: {} withdrawal request to process from block at " + "slot {}",
        withdrawalRequests.size(),
        slot);

    withdrawalRequests.forEach(
        withdrawalRequest -> {
          LOG.debug(
              "process_withdrawal_request: processing withdrawal request {}", withdrawalRequest);

          // If partial withdrawal queue is full, only full exits are processed
          final boolean isFullExitRequest =
              withdrawalRequest.getAmount().equals(FULL_EXIT_REQUEST_AMOUNT);
          final boolean partialWithdrawalsQueueFull =
              state.toVersionElectra().orElseThrow().getPendingPartialWithdrawals().size()
                  >= specConfigElectra.getPendingPartialWithdrawalsLimit();
          if (partialWithdrawalsQueueFull && !isFullExitRequest) {
            LOG.debug("process_withdrawal_request: partial withdrawal queue is full");
            return;
          }

          final Optional<Integer> maybeValidatorIndex =
              validatorsUtil.getValidatorIndex(state, withdrawalRequest.getValidatorPublicKey());
          if (maybeValidatorIndex.isEmpty()) {
            LOG.debug(
                "process_withdrawal_request: no matching validator for public key {}",
                withdrawalRequest.getValidatorPublicKey());
            return;
          }

          final int validatorIndex = maybeValidatorIndex.get();
          final Validator validator = state.getValidators().get(validatorIndex);

          // Check if validator has an execution address set
          final boolean hasExecutionAddress =
              predicatesElectra.hasExecutionWithdrawalCredential(validator);
          if (!hasExecutionAddress) {
            LOG.debug(
                "process_withdrawal_request: validator index {} does not have withdrawal credentials set",
                validatorIndex);
            return;
          }

          // Check withdrawalRequest source_address matches validator eth1 withdrawal credentials
          final Bytes20 validatorExecutionAddress =
              new Bytes20(validator.getWithdrawalCredentials().slice(12));
          final Bytes20 withdrawalRequestSourceAddress = withdrawalRequest.getSourceAddress();
          final boolean isCorrectSourceAddress =
              validatorExecutionAddress.equals(withdrawalRequestSourceAddress);
          if (!isCorrectSourceAddress) {
            LOG.debug(
                "process_withdrawal_request: WithdrawalRequest source_address {} does not match "
                    + "validator {} withdrawal credentials {}",
                withdrawalRequestSourceAddress,
                validatorIndex,
                validatorExecutionAddress);
            return;
          }

          // Check if validator is active
          final boolean isValidatorActive = predicates.isActiveValidator(validator, currentEpoch);
          if (!isValidatorActive) {
            LOG.debug("process_withdrawal_request: Validator {} is not active", validatorIndex);
            return;
          }

          // Check if validator has already initiated exit
          final boolean hasInitiatedExit = !validator.getExitEpoch().equals(FAR_FUTURE_EPOCH);
          if (hasInitiatedExit) {
            LOG.debug(
                "process_withdrawal_request: Validator {} has already initiated exit",
                validatorIndex);
            return;
          }

          // Check if validator has been active long enough
          final boolean validatorActiveLongEnough =
              currentEpoch.isGreaterThanOrEqualTo(
                  validator.getActivationEpoch().plus(specConfig.getShardCommitteePeriod()));
          if (!validatorActiveLongEnough) {
            LOG.debug(
                "process_withdrawal_request: Validator {} is not active long enough",
                validatorIndex);
            return;
          }

          final UInt64 pendingBalanceToWithdraw =
              validatorsUtil.getPendingBalanceToWithdraw(state, validatorIndex);
          if (isFullExitRequest) {
            // Only exit validator if it has no pending withdrawals in the queue
            if (pendingBalanceToWithdraw.isZero()) {
              LOG.debug(
                  "process_withdrawal_request: Initiating exit for validator {}", validatorIndex);

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

            LOG.debug(
                "process_withdrawal_request: Creating pending partial withdrawal for validator {}",
                validatorIndex);

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

  /*
   Implements process_deposit_request from consensus-specs (EIP-6110)
  */
  @Override
  public void processDepositRequests(
      final MutableBeaconState state, final List<DepositRequest> depositRequests) {
    final MutableBeaconStateElectra electraState = MutableBeaconStateElectra.required(state);
    for (DepositRequest depositRequest : depositRequests) {
      // process_deposit_request
      if (electraState
          .getDepositRequestsStartIndex()
          .equals(SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX)) {
        electraState.setDepositRequestsStartIndex(depositRequest.getIndex());
      }
      applyDeposit(
          state,
          depositRequest.getPubkey(),
          depositRequest.getWithdrawalCredentials(),
          depositRequest.getAmount(),
          depositRequest.getSignature(),
          Optional.empty(),
          false);
    }
  }

  /**
   * Implements process_consolidation_request from consensus-spec (EIP-7251)
   *
   * @see <a href="https://github.com/ethereum/consensus-specs/blob/dev/specs/electra/beacon-chain
   *     .md#new-process_consolidation_request"/>
   */
  @Override
  public void processConsolidationRequests(
      final MutableBeaconState state, final List<ConsolidationRequest> consolidationRequests) {
    LOG.debug(
        "process_consolidation_request: {} consolidation request to process from block at "
            + "slot {}",
        consolidationRequests.size(),
        state.getSlot());

    final MutableBeaconStateElectra electraState = MutableBeaconStateElectra.required(state);
    consolidationRequests.forEach(
        consolidationRequest -> processConsolidationRequest(electraState, consolidationRequest));
  }

  private void processConsolidationRequest(
      final MutableBeaconStateElectra state, final ConsolidationRequest consolidationRequest) {
    final UInt64 slot = state.getSlot();
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(slot);

    // If the pending consolidations queue is full, consolidation requests are ignored
    if (state.getPendingConsolidations().size()
        == specConfigElectra.getPendingConsolidationsLimit()) {
      LOG.debug("process_consolidation_request: consolidation queue is full");
      return;
    }

    // If there is too little available consolidation churn limit, consolidation requests are
    // ignored
    if (BeaconStateAccessorsElectra.required(beaconStateAccessors)
        .getConsolidationChurnLimit(state)
        .isLessThanOrEqualTo(specConfigElectra.getMinActivationBalance())) {
      LOG.debug("process_consolidation_request: not enough consolidation churn limit available");
      return;
    }

    // Verify source_pubkey exists
    final Optional<Integer> maybeSourceValidatorIndex =
        validatorsUtil.getValidatorIndex(state, consolidationRequest.getSourcePubkey());
    if (maybeSourceValidatorIndex.isEmpty()) {
      LOG.debug(
          "process_consolidation_request: source_pubkey {} not found",
          consolidationRequest.getSourcePubkey());
      return;
    }

    // Verify target_pubkey exists
    final Optional<Integer> maybeTargetValidatorIndex =
        validatorsUtil.getValidatorIndex(state, consolidationRequest.getTargetPubkey());
    if (maybeTargetValidatorIndex.isEmpty()) {
      LOG.debug(
          "process_consolidation_request: target_pubkey {} not found",
          consolidationRequest.getTargetPubkey());
      return;
    }

    // Verify that source != target, so a consolidation cannot be used as an exit.
    if (maybeSourceValidatorIndex.get().equals(maybeTargetValidatorIndex.get())) {
      LOG.debug("process_consolidation_request: source_pubkey and target_pubkey must be different");
      return;
    }

    final Validator sourceValidator = state.getValidators().get(maybeSourceValidatorIndex.get());
    final Validator targetValidator = state.getValidators().get(maybeTargetValidatorIndex.get());

    // Verify source withdrawal credentials
    final boolean sourceHasExecutionWithdrawalCredentials =
        predicatesElectra.hasExecutionWithdrawalCredential(sourceValidator);

    final Eth1Address sourceValidatorExecutionAddress =
        Predicates.getExecutionAddressUnchecked(sourceValidator.getWithdrawalCredentials());
    final boolean sourceHasCorrectCredentials =
        sourceValidatorExecutionAddress.equals(
            Eth1Address.fromBytes(consolidationRequest.getSourceAddress().getWrappedBytes()));
    if (!(sourceHasExecutionWithdrawalCredentials && sourceHasCorrectCredentials)) {
      LOG.debug("process_consolidation_request: invalid source credentials");
      return;
    }

    // Verify that target has execution withdrawal credentials
    if (!predicatesElectra.hasExecutionWithdrawalCredential(targetValidator)) {
      LOG.debug("process_consolidation_request: invalid target credentials");
      return;
    }

    // Verify the source and the target are active
    if (!predicatesElectra.isActiveValidator(sourceValidator, currentEpoch)) {
      LOG.debug("process_consolidation_request: source validator is inactive");
      return;
    }
    if (!predicatesElectra.isActiveValidator(targetValidator, currentEpoch)) {
      LOG.debug("process_consolidation_request: target validator is inactive");
      return;
    }

    // Verify exits for source and target have not been initiated
    if (!sourceValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      LOG.debug("process_consolidation_request: source validator is exiting");
      return;
    }
    if (!targetValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      LOG.debug("process_consolidation_request: target validator is exiting");
      return;
    }

    // Initiate source validator exit and append pending consolidation
    final UInt64 exitEpoch =
        beaconStateMutatorsElectra.computeConsolidationEpochAndUpdateChurn(
            state, sourceValidator.getEffectiveBalance());
    final UInt64 withdrawableEpoch =
        exitEpoch.plus(specConfigElectra.getMinValidatorWithdrawabilityDelay());

    state
        .getValidators()
        .update(
            maybeSourceValidatorIndex.get(),
            v -> v.withExitEpoch(exitEpoch).withWithdrawableEpoch(withdrawableEpoch));
    LOG.debug(
        "process_consolidation_request: updated validator {} with exit_epoch = {}, withdrawable_epoch = {}",
        maybeSourceValidatorIndex.get(),
        exitEpoch,
        withdrawableEpoch);

    final PendingConsolidation pendingConsolidation =
        new PendingConsolidation(
            schemaDefinitionsElectra.getPendingConsolidationSchema(),
            SszUInt64.of(UInt64.valueOf(maybeSourceValidatorIndex.get())),
            SszUInt64.of(UInt64.valueOf(maybeTargetValidatorIndex.get())));
    state.getPendingConsolidations().append(pendingConsolidation);

    LOG.debug("process_consolidation_request: created {}", pendingConsolidation);
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
    int validatorIndex = -1;
    for (int i = state.getValidators().size() - 1; i >= 0; i--) {
      if (state.getValidators().get(i).getPublicKey().equals(pubkey)) {
        validatorIndex = i;
        break;
      }
    }
    if (validatorIndex < 0) {
      throw new IllegalStateException(
          "Could not locate validator " + pubkey + " after adding to state.");
    }
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
