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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigElectra.FULL_EXIT_REQUEST_AMOUNT;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.ExpectedWithdrawals;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
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
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class BlockProcessorElectra extends BlockProcessorDeneb {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfigElectra specConfigElectra;
  private final PredicatesElectra predicatesElectra;
  private final BeaconStateMutatorsElectra beaconStateMutatorsElectra;
  private final BeaconStateAccessorsElectra beaconStateAccessorsElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;
  private final ExecutionRequestsDataCodec executionRequestsDataCodec;

  public BlockProcessorElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final MiscHelpersElectra miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsElectra beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsElectra schemaDefinitions,
      final ExecutionRequestsDataCodec executionRequestsDataCodec) {
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
    this.beaconStateAccessorsElectra = beaconStateAccessors;
    this.schemaDefinitionsElectra = schemaDefinitions;
    this.executionRequestsDataCodec = executionRequestsDataCodec;
  }

  @Override
  public NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state, final BeaconBlockBody beaconBlockBody)
      throws BlockProcessingException {
    final ExecutionPayload executionPayload = extractExecutionPayload(beaconBlockBody);
    final SszList<SszKZGCommitment> blobKzgCommitments = extractBlobKzgCommitments(beaconBlockBody);
    final List<VersionedHash> versionedHashes =
        blobKzgCommitments.stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .toList();
    final Bytes32 parentBeaconBlockRoot = state.getLatestBlockHeader().getParentRoot();
    final ExecutionRequests executionRequests =
        BeaconBlockBodyElectra.required(beaconBlockBody).getExecutionRequests();
    return new NewPayloadRequest(
        executionPayload,
        versionedHashes,
        parentBeaconBlockRoot,
        executionRequestsDataCodec.encode(executionRequests));
  }

  /*
   * <spec function="process_operations" fork="electra" style="diff">
   * --- capella
   * +++ electra
   * @@ -1,5 +1,9 @@
   *  def process_operations(state: BeaconState, body: BeaconBlockBody) -> None:
   * -    assert len(body.deposits) == min(MAX_DEPOSITS, state.eth1_data.deposit_count - state.eth1_deposit_index)
   * +    eth1_deposit_index_limit = min(state.eth1_data.deposit_count, state.deposit_requests_start_index)
   * +    if state.eth1_deposit_index < eth1_deposit_index_limit:
   * +        assert len(body.deposits) == min(MAX_DEPOSITS, eth1_deposit_index_limit - state.eth1_deposit_index)
   * +    else:
   * +        assert len(body.deposits) == 0
   *
   *      def for_ops(operations: Sequence[Any], fn: Callable[[BeaconState, Any], None]) -> None:
   *          for operation in operations:
   * @@ -11,3 +15,6 @@
   *      for_ops(body.deposits, process_deposit)
   *      for_ops(body.voluntary_exits, process_voluntary_exit)
   *      for_ops(body.bls_to_execution_changes, process_bls_to_execution_change)
   * +    for_ops(body.execution_requests.deposits, process_deposit_request)
   * +    for_ops(body.execution_requests.withdrawals, process_withdrawal_request)
   * +    for_ops(body.execution_requests.consolidations, process_consolidation_request)
   * </spec>
   */
  @Override
  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    super.processOperationsNoValidation(
        state, body, indexedAttestationCache, validatorExitContextSupplier);

    safelyProcess(
        () -> {
          final ExecutionRequests executionRequests =
              BeaconBlockBodyElectra.required(body).getExecutionRequests();

          processDepositRequests(state, executionRequests.getDeposits());
          processWithdrawalRequests(
              state, executionRequests.getWithdrawals(), validatorExitContextSupplier);
          processConsolidationRequests(state, executionRequests.getConsolidations());
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

  /*
   * <spec function="process_deposit_request" fork="electra">
   * def process_deposit_request(state: BeaconState, deposit_request: DepositRequest) -> None:
   *     # Set deposit request start index
   *     if state.deposit_requests_start_index == UNSET_DEPOSIT_REQUESTS_START_INDEX:
   *         state.deposit_requests_start_index = deposit_request.index
   *
   *     # Create pending deposit
   *     state.pending_deposits.append(PendingDeposit(
   *         pubkey=deposit_request.pubkey,
   *         withdrawal_credentials=deposit_request.withdrawal_credentials,
   *         amount=deposit_request.amount,
   *         signature=deposit_request.signature,
   *         slot=state.slot,
   *     ))
   * </spec>
   */
  @Override
  public void processDepositRequests(
      final MutableBeaconState state, final List<DepositRequest> depositRequests) {
    final MutableBeaconStateElectra electraState = MutableBeaconStateElectra.required(state);
    final SszMutableList<PendingDeposit> pendingDeposits =
        MutableBeaconStateElectra.required(state).getPendingDeposits();
    for (DepositRequest depositRequest : depositRequests) {
      // process_deposit_request
      if (electraState
          .getDepositRequestsStartIndex()
          .equals(SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX)) {
        electraState.setDepositRequestsStartIndex(depositRequest.getIndex());
      }

      final PendingDeposit deposit =
          schemaDefinitionsElectra
              .getPendingDepositSchema()
              .create(
                  new SszPublicKey(depositRequest.getPubkey()),
                  SszBytes32.of(depositRequest.getWithdrawalCredentials()),
                  SszUInt64.of(depositRequest.getAmount()),
                  new SszSignature(depositRequest.getSignature()),
                  SszUInt64.of(state.getSlot()));
      pendingDeposits.append(deposit);
    }
  }

  /*
   * <spec function="process_withdrawal_request" fork="electra">
   * def process_withdrawal_request(
   *     state: BeaconState,
   *     withdrawal_request: WithdrawalRequest
   * ) -> None:
   *     amount = withdrawal_request.amount
   *     is_full_exit_request = amount == FULL_EXIT_REQUEST_AMOUNT
   *
   *     # If partial withdrawal queue is full, only full exits are processed
   *     if len(state.pending_partial_withdrawals) == PENDING_PARTIAL_WITHDRAWALS_LIMIT and not is_full_exit_request:
   *         return
   *
   *     validator_pubkeys = [v.pubkey for v in state.validators]
   *     # Verify pubkey exists
   *     request_pubkey = withdrawal_request.validator_pubkey
   *     if request_pubkey not in validator_pubkeys:
   *         return
   *     index = ValidatorIndex(validator_pubkeys.index(request_pubkey))
   *     validator = state.validators[index]
   *
   *     # Verify withdrawal credentials
   *     has_correct_credential = has_execution_withdrawal_credential(validator)
   *     is_correct_source_address = (
   *         validator.withdrawal_credentials[12:] == withdrawal_request.source_address
   *     )
   *     if not (has_correct_credential and is_correct_source_address):
   *         return
   *     # Verify the validator is active
   *     if not is_active_validator(validator, get_current_epoch(state)):
   *         return
   *     # Verify exit has not been initiated
   *     if validator.exit_epoch != FAR_FUTURE_EPOCH:
   *         return
   *     # Verify the validator has been active long enough
   *     if get_current_epoch(state) < validator.activation_epoch + SHARD_COMMITTEE_PERIOD:
   *         return
   *
   *     pending_balance_to_withdraw = get_pending_balance_to_withdraw(state, index)
   *
   *     if is_full_exit_request:
   *         # Only exit validator if it has no pending withdrawals in the queue
   *         if pending_balance_to_withdraw == 0:
   *             initiate_validator_exit(state, index)
   *         return
   *
   *     has_sufficient_effective_balance = validator.effective_balance >= MIN_ACTIVATION_BALANCE
   *     has_excess_balance = state.balances[index] > MIN_ACTIVATION_BALANCE + pending_balance_to_withdraw
   *
   *     # Only allow partial withdrawals with compounding withdrawal credentials
   *     if has_compounding_withdrawal_credential(validator) and has_sufficient_effective_balance and has_excess_balance:
   *         to_withdraw = min(
   *             state.balances[index] - MIN_ACTIVATION_BALANCE - pending_balance_to_withdraw,
   *             amount
   *         )
   *         exit_queue_epoch = compute_exit_epoch_and_update_churn(state, to_withdraw)
   *         withdrawable_epoch = Epoch(exit_queue_epoch + MIN_VALIDATOR_WITHDRAWABILITY_DELAY)
   *         state.pending_partial_withdrawals.append(PendingPartialWithdrawal(
   *             validator_index=index,
   *             amount=to_withdraw,
   *             withdrawable_epoch=withdrawable_epoch,
   *         ))
   * </spec>
   */
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
                  == specConfigElectra.getPendingPartialWithdrawalsLimit();
          if (partialWithdrawalsQueueFull && !isFullExitRequest) {
            LOG.debug("process_withdrawal_request: partial withdrawal queue is full");
            return;
          }

          final Optional<Integer> maybeValidatorIndex =
              validatorsUtil.getValidatorIndex(state, withdrawalRequest.getValidatorPubkey());
          if (maybeValidatorIndex.isEmpty()) {
            LOG.debug(
                "process_withdrawal_request: no matching validator for public key {}",
                withdrawalRequest.getValidatorPubkey().toAbbreviatedString());
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
            }
            return;
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
   * <spec function="process_consolidation_request" fork="electra">
   * def process_consolidation_request(
   *     state: BeaconState,
   *     consolidation_request: ConsolidationRequest
   * ) -> None:
   *     if is_valid_switch_to_compounding_request(state, consolidation_request):
   *         validator_pubkeys = [v.pubkey for v in state.validators]
   *         request_source_pubkey = consolidation_request.source_pubkey
   *         source_index = ValidatorIndex(validator_pubkeys.index(request_source_pubkey))
   *         switch_to_compounding_validator(state, source_index)
   *         return
   *
   *     # Verify that source != target, so a consolidation cannot be used as an exit.
   *     if consolidation_request.source_pubkey == consolidation_request.target_pubkey:
   *         return
   *     # If the pending consolidations queue is full, consolidation requests are ignored
   *     if len(state.pending_consolidations) == PENDING_CONSOLIDATIONS_LIMIT:
   *         return
   *     # If there is too little available consolidation churn limit, consolidation requests are ignored
   *     if get_consolidation_churn_limit(state) <= MIN_ACTIVATION_BALANCE:
   *         return
   *
   *     validator_pubkeys = [v.pubkey for v in state.validators]
   *     # Verify pubkeys exists
   *     request_source_pubkey = consolidation_request.source_pubkey
   *     request_target_pubkey = consolidation_request.target_pubkey
   *     if request_source_pubkey not in validator_pubkeys:
   *         return
   *     if request_target_pubkey not in validator_pubkeys:
   *         return
   *     source_index = ValidatorIndex(validator_pubkeys.index(request_source_pubkey))
   *     target_index = ValidatorIndex(validator_pubkeys.index(request_target_pubkey))
   *     source_validator = state.validators[source_index]
   *     target_validator = state.validators[target_index]
   *
   *     # Verify source withdrawal credentials
   *     has_correct_credential = has_execution_withdrawal_credential(source_validator)
   *     is_correct_source_address = (
   *         source_validator.withdrawal_credentials[12:] == consolidation_request.source_address
   *     )
   *     if not (has_correct_credential and is_correct_source_address):
   *         return
   *
   *     # Verify that target has compounding withdrawal credentials
   *     if not has_compounding_withdrawal_credential(target_validator):
   *         return
   *
   *     # Verify the source and the target are active
   *     current_epoch = get_current_epoch(state)
   *     if not is_active_validator(source_validator, current_epoch):
   *         return
   *     if not is_active_validator(target_validator, current_epoch):
   *         return
   *     # Verify exits for source and target have not been initiated
   *     if source_validator.exit_epoch != FAR_FUTURE_EPOCH:
   *         return
   *     if target_validator.exit_epoch != FAR_FUTURE_EPOCH:
   *         return
   *     # Verify the source has been active long enough
   *     if current_epoch < source_validator.activation_epoch + SHARD_COMMITTEE_PERIOD:
   *         return
   *     # Verify the source has no pending withdrawals in the queue
   *     if get_pending_balance_to_withdraw(state, source_index) > 0:
   *         return
   *
   *     # Initiate source validator exit and append pending consolidation
   *     source_validator.exit_epoch = compute_consolidation_epoch_and_update_churn(
   *         state, source_validator.effective_balance
   *     )
   *     source_validator.withdrawable_epoch = Epoch(
   *         source_validator.exit_epoch + MIN_VALIDATOR_WITHDRAWABILITY_DELAY
   *     )
   *     state.pending_consolidations.append(PendingConsolidation(
   *         source_index=source_index,
   *         target_index=target_index
   *     ))
   * </spec>
   */
  @Override
  public void processConsolidationRequests(
      final MutableBeaconState state, final List<ConsolidationRequest> consolidationRequests) {
    LOG.debug(
        "process_consolidation_request: {} consolidation requests to process from block at "
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

    if (isValidSwitchToCompoundingRequest(state, consolidationRequest)) {
      LOG.debug(
          "process_consolidation_request: switching validator {} to compounding address",
          consolidationRequest.getSourcePubkey().toAbbreviatedString());
      validatorsUtil
          .getValidatorIndex(state, consolidationRequest.getSourcePubkey())
          .ifPresent(
              sourceValidatorIndex ->
                  beaconStateMutatorsElectra.switchToCompoundingValidator(
                      state, sourceValidatorIndex));
      return;
    }

    // Verify that source != target, so a consolidation cannot be used as an exit
    if (consolidationRequest.getSourcePubkey().equals(consolidationRequest.getTargetPubkey())) {
      LOG.debug(
          "process_consolidation_request: source_pubkey and target_pubkey must be different (pubkey = {})",
          consolidationRequest.getSourcePubkey().toAbbreviatedString());
      return;
    }

    // If the pending consolidations queue is full, consolidation requests are ignored
    if (state.getPendingConsolidations().size()
        == specConfigElectra.getPendingConsolidationsLimit()) {
      LOG.debug("process_consolidation_request: consolidation queue is full");
      return;
    }

    // If there is too little available consolidation churn limit, consolidation requests are
    // ignored
    if (beaconStateAccessorsElectra
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
          consolidationRequest.getSourcePubkey().toAbbreviatedString());
      return;
    }

    // Verify target_pubkey exists
    final Optional<Integer> maybeTargetValidatorIndex =
        validatorsUtil.getValidatorIndex(state, consolidationRequest.getTargetPubkey());
    if (maybeTargetValidatorIndex.isEmpty()) {
      LOG.debug(
          "process_consolidation_request: target_pubkey {} not found",
          consolidationRequest.getTargetPubkey().toAbbreviatedString());
      return;
    }

    final int sourceValidatorIndex = maybeSourceValidatorIndex.get();
    final Validator sourceValidator = state.getValidators().get(sourceValidatorIndex);
    final int targetValidatorIndex = maybeTargetValidatorIndex.get();
    final Validator targetValidator = state.getValidators().get(targetValidatorIndex);

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

    // Verify that target has compounding withdrawal credentials
    if (!predicatesElectra.hasCompoundingWithdrawalCredential(targetValidator)) {
      LOG.debug("process_consolidation_request: invalid target credentials");
      return;
    }

    // Verify the source and the target are active
    if (!predicatesElectra.isActiveValidator(sourceValidator, currentEpoch)) {
      LOG.debug(
          "process_consolidation_request: source validator {} is inactive", sourceValidatorIndex);
      return;
    }
    if (!predicatesElectra.isActiveValidator(targetValidator, currentEpoch)) {
      LOG.debug(
          "process_consolidation_request: target validator {} is inactive", targetValidatorIndex);
      return;
    }

    // Verify exits for source and target have not been initiated
    if (!sourceValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      LOG.debug(
          "process_consolidation_request: source validator {} is exiting", sourceValidatorIndex);
      return;
    }
    if (!targetValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      LOG.debug(
          "process_consolidation_request: target validator {} is exiting", targetValidatorIndex);
      return;
    }

    // Verify the source has been active long enough
    if (currentEpoch.isLessThan(
        sourceValidator.getActivationEpoch().plus(specConfig.getShardCommitteePeriod()))) {
      LOG.debug("process_consolidation_request: source has not been active long enough");
      return;
    }
    // Verify the source has no pending withdrawals in the queue
    if (beaconStateAccessorsElectra
        .getPendingBalanceToWithdraw(state, sourceValidatorIndex)
        .isGreaterThan(ZERO)) {
      LOG.debug("process_consolidation_request: source has pending withdrawals in the queue");
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
            sourceValidatorIndex,
            v -> v.withExitEpoch(exitEpoch).withWithdrawableEpoch(withdrawableEpoch));
    LOG.debug(
        "process_consolidation_request: updated validator {} with exit_epoch = {}, withdrawable_epoch = {}",
        sourceValidatorIndex,
        exitEpoch,
        withdrawableEpoch);

    final PendingConsolidation pendingConsolidation =
        new PendingConsolidation(
            schemaDefinitionsElectra.getPendingConsolidationSchema(),
            SszUInt64.of(UInt64.valueOf(sourceValidatorIndex)),
            SszUInt64.of(UInt64.valueOf(targetValidatorIndex)));
    state.getPendingConsolidations().append(pendingConsolidation);

    LOG.debug("process_consolidation_request: created {}", pendingConsolidation);
  }

  /*
   * <spec function="is_valid_switch_to_compounding_request" fork="electra">
   * def is_valid_switch_to_compounding_request(
   *     state: BeaconState,
   *     consolidation_request: ConsolidationRequest
   * ) -> bool:
   *     # Switch to compounding requires source and target be equal
   *     if consolidation_request.source_pubkey != consolidation_request.target_pubkey:
   *         return False
   *
   *     # Verify pubkey exists
   *     source_pubkey = consolidation_request.source_pubkey
   *     validator_pubkeys = [v.pubkey for v in state.validators]
   *     if source_pubkey not in validator_pubkeys:
   *         return False
   *
   *     source_validator = state.validators[ValidatorIndex(validator_pubkeys.index(source_pubkey))]
   *
   *     # Verify request has been authorized
   *     if source_validator.withdrawal_credentials[12:] != consolidation_request.source_address:
   *         return False
   *
   *     # Verify source withdrawal credentials
   *     if not has_eth1_withdrawal_credential(source_validator):
   *         return False
   *
   *     # Verify the source is active
   *     current_epoch = get_current_epoch(state)
   *     if not is_active_validator(source_validator, current_epoch):
   *         return False
   *
   *     # Verify exit for source has not been initiated
   *     if source_validator.exit_epoch != FAR_FUTURE_EPOCH:
   *         return False
   *
   *     return True
   * </spec>
   */
  @Override
  public boolean isValidSwitchToCompoundingRequest(
      final BeaconState state, final ConsolidationRequest consolidationRequest) {

    // Switch to compounding requires source and target be equal
    if (!consolidationRequest.getSourcePubkey().equals(consolidationRequest.getTargetPubkey())) {
      return false;
    }

    // Verify source_pubkey exists
    final Optional<Integer> maybeSourceValidatorIndex =
        validatorsUtil.getValidatorIndex(state, consolidationRequest.getSourcePubkey());
    if (maybeSourceValidatorIndex.isEmpty()) {
      return false;
    }

    final int sourceValidatorIndex = maybeSourceValidatorIndex.get();
    final Validator sourceValidator = state.getValidators().get(sourceValidatorIndex);

    // Verify request has been authorized
    final Eth1Address sourceValidatorExecutionAddress =
        Predicates.getExecutionAddressUnchecked(sourceValidator.getWithdrawalCredentials());
    if (!sourceValidatorExecutionAddress.equals(
        Eth1Address.fromBytes(consolidationRequest.getSourceAddress().getWrappedBytes()))) {
      return false;
    }

    // Verify source withdrawal credentials
    if (!predicatesElectra.hasEth1WithdrawalCredential(sourceValidator)) {
      return false;
    }

    // Verify the source is active
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    if (!predicatesElectra.isActiveValidator(sourceValidator, currentEpoch)) {
      return false;
    }

    // Verify exit for source has not been initiated
    return sourceValidator.getExitEpoch().equals(FAR_FUTURE_EPOCH);
  }

  /*
   * <spec function="apply_deposit" fork="electra">
   * def apply_deposit(state: BeaconState,
   *                   pubkey: BLSPubkey,
   *                   withdrawal_credentials: Bytes32,
   *                   amount: uint64,
   *                   signature: BLSSignature) -> None:
   *     validator_pubkeys = [v.pubkey for v in state.validators]
   *     if pubkey not in validator_pubkeys:
   *         # Verify the deposit signature (proof of possession) which is not checked by the deposit contract
   *         if is_valid_deposit_signature(pubkey, withdrawal_credentials, amount, signature):
   *             add_validator_to_registry(state, pubkey, withdrawal_credentials, Gwei(0))  # [Modified in Electra:EIP7251]
   *         else:
   *             return
   *
   *     # Increase balance by deposit amount
   *     # [Modified in Electra:EIP7251]
   *     state.pending_deposits.append(PendingDeposit(
   *         pubkey=pubkey,
   *         withdrawal_credentials=withdrawal_credentials,
   *         amount=amount,
   *         signature=signature,
   *         slot=GENESIS_SLOT  # Use GENESIS_SLOT to distinguish from a pending deposit request
   *     ))
   * </spec>
   */
  @Override
  public void applyDeposit(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {

    // Find the validator index associated with this deposit, if it exists
    final Optional<Integer> existingIndex =
        maybePubkeyToIndexMap
            .flatMap(
                pubkeyToIndexMap -> {
                  if (pubkeyToIndexMap.containsKey(pubkey)) {
                    return Optional.of(pubkeyToIndexMap.getInt(pubkey));
                  } else {
                    pubkeyToIndexMap.put(pubkey, state.getValidators().size());
                    return Optional.empty();
                  }
                })
            .or(() -> validatorsUtil.getValidatorIndex(state, pubkey));

    if (existingIndex.isEmpty()) {
      // This is a new validator
      // Verify the deposit signature (proof of possession) which is not checked by the deposit
      // contract
      if (signatureAlreadyVerified
          || miscHelpers.isValidDepositSignature(
              pubkey, withdrawalCredentials, amount, signature)) {
        beaconStateMutators.addValidatorToRegistry(state, pubkey, withdrawalCredentials, ZERO);
        final PendingDeposit deposit =
            schemaDefinitionsElectra
                .getPendingDepositSchema()
                .create(
                    new SszPublicKey(pubkey),
                    SszBytes32.of(withdrawalCredentials),
                    SszUInt64.of(amount),
                    new SszSignature(signature),
                    SszUInt64.of(SpecConfig.GENESIS_SLOT));
        MutableBeaconStateElectra.required(state).getPendingDeposits().append(deposit);
      } else {
        handleInvalidDeposit(pubkey, maybePubkeyToIndexMap);
      }
    } else {
      final PendingDeposit deposit =
          schemaDefinitionsElectra
              .getPendingDepositSchema()
              .create(
                  new SszPublicKey(pubkey),
                  SszBytes32.of(withdrawalCredentials),
                  SszUInt64.of(amount),
                  new SszSignature(signature),
                  SszUInt64.of(SpecConfig.GENESIS_SLOT));
      MutableBeaconStateElectra.required(state).getPendingDeposits().append(deposit);
    }
  }

  @Override
  public void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {
    state.setEth1DepositIndex(state.getEth1DepositIndex().plus(UInt64.ONE));

    applyDeposit(
        state,
        deposit.getData().getPubkey(),
        deposit.getData().getWithdrawalCredentials(),
        deposit.getData().getAmount(),
        deposit.getData().getSignature(),
        maybePubkeyToIndexMap,
        signatureAlreadyVerified);
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
        beaconStateAccessorsElectra.getCommitteeCountPerSlot(
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
    int committeeOffset = 0;
    for (final UInt64 committeeIndex : committeeIndices) {
      if (committeeIndex.isGreaterThanOrEqualTo(committeeCountPerSlot)) {
        return Optional.of(AttestationInvalidReason.COMMITTEE_INDEX_TOO_HIGH);
      }
      final IntList committee =
          beaconStateAccessorsElectra.getBeaconCommittee(state, slot, committeeIndex);
      final int currentCommitteeOffset = committeeOffset;
      final boolean committeeHasAtLeastOneAttester =
          IntStream.range(0, committee.size())
              .anyMatch(
                  committeeParticipantIndex ->
                      aggregationBits.isSet(currentCommitteeOffset + committeeParticipantIndex));
      if (!committeeHasAtLeastOneAttester) {
        return Optional.of(AttestationInvalidReason.PARTICIPANTS_COUNT_MISMATCH);
      }
      committeeOffset += committee.size();
    }
    if (committeeOffset != aggregationBits.size()) {
      return Optional.of(AttestationInvalidReason.PARTICIPANTS_COUNT_MISMATCH);
    }
    return Optional.empty();
  }
}
