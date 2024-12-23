/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.electra.statetransition.epoch;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.capella.statetransition.epoch.EpochProcessorCapella;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class EpochProcessorElectra extends EpochProcessorCapella {

  private final UInt64 minActivationBalance;
  private final BeaconStateAccessorsElectra stateAccessorsElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;

  public EpochProcessorElectra(
      final SpecConfigElectra specConfig,
      final MiscHelpersElectra miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitions schemaDefinitions,
      final TimeProvider timeProvider) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions,
        timeProvider);
    this.minActivationBalance =
        specConfig.toVersionElectra().orElseThrow().getMinActivationBalance();
    this.stateAccessorsElectra = BeaconStateAccessorsElectra.required(beaconStateAccessors);
    this.schemaDefinitionsElectra = SchemaDefinitionsElectra.required(schemaDefinitions);
  }

  /*
   * <spec function="process_registry_updates" fork="electra">
   * def process_registry_updates(state: BeaconState) -> None:
   *     # Process activation eligibility and ejections
   *     for index, validator in enumerate(state.validators):
   *         if is_eligible_for_activation_queue(validator):  # [Modified in Electra:EIP7251]
   *             validator.activation_eligibility_epoch = get_current_epoch(state) + 1
   *
   *         if (
   *             is_active_validator(validator, get_current_epoch(state))
   *             and validator.effective_balance <= EJECTION_BALANCE
   *         ):
   *             initiate_validator_exit(state, ValidatorIndex(index))  # [Modified in Electra:EIP7251]
   *
   *     # Activate all eligible validators
   *     # [Modified in Electra:EIP7251]
   *     activation_epoch = compute_activation_exit_epoch(get_current_epoch(state))
   *     for validator in state.validators:
   *         if is_eligible_for_activation(state, validator):
   *             validator.activation_epoch = activation_epoch
   * </spec>
   */
  @Override
  public void processRegistryUpdates(
      final MutableBeaconState state, final List<ValidatorStatus> statuses)
      throws EpochProcessingException {
    try {

      // Process activation eligibility and ejections
      final SszMutableList<Validator> validators = state.getValidators();
      final UInt64 currentEpoch = stateAccessorsElectra.getCurrentEpoch(state);
      final UInt64 finalizedEpoch = state.getFinalizedCheckpoint().getEpoch();
      final UInt64 ejectionBalance = specConfig.getEjectionBalance();
      final Supplier<BeaconStateMutators.ValidatorExitContext> validatorExitContextSupplier =
          beaconStateMutators.createValidatorExitContextSupplier(state);

      final UInt64 activationEpoch =
          miscHelpers.computeActivationExitEpoch(miscHelpers.computeEpochAtSlot(state.getSlot()));

      for (int index = 0; index < validators.size(); index++) {
        final ValidatorStatus status = statuses.get(index);

        if (isEligibleForActivationQueue(status)) {
          final Validator validator = validators.get(index);
          if (validator.getActivationEligibilityEpoch().equals(SpecConfig.FAR_FUTURE_EPOCH)) {
            state
                .getValidators()
                .update(
                    index, v -> v.withActivationEligibilityEpoch(currentEpoch.plus(UInt64.ONE)));
          }
        } else if (status.isActiveInCurrentEpoch()
            && status.getCurrentEpochEffectiveBalance().isLessThanOrEqualTo(ejectionBalance)) {
          beaconStateMutators.initiateValidatorExit(state, index, validatorExitContextSupplier);
        }
        // activate all eligible validators
        final Validator validator = validators.get(index);
        if (isEligibleForActivation(finalizedEpoch, validator)) {
          state.getValidators().update(index, v -> v.withActivationEpoch(activationEpoch));
        }
      }
    } catch (IllegalArgumentException e) {
      throw new EpochProcessingException(e);
    }
  }

  protected boolean isEligibleForActivation(
      final UInt64 finalizedEpoch, final Validator validator) {
    return validator.getActivationEpoch().equals(FAR_FUTURE_EPOCH)
        && validator.getActivationEligibilityEpoch().isLessThanOrEqualTo(finalizedEpoch);
  }

  /*
   * <spec function="is_eligible_for_activation_queue" fork="electra">
   * def is_eligible_for_activation_queue(validator: Validator) -> bool:
   *     """
   *     Check if ``validator`` is eligible to be placed into the activation queue.
   *     """
   *     return (
   *         validator.activation_eligibility_epoch == FAR_FUTURE_EPOCH
   *         and validator.effective_balance >= MIN_ACTIVATION_BALANCE  # [Modified in Electra:EIP7251]
   *     )
   * </spec>
   */
  @Override
  protected boolean isEligibleForActivationQueue(final ValidatorStatus status) {
    return !status.isActiveInCurrentEpoch()
        && status.getCurrentEpochEffectiveBalance().isGreaterThanOrEqualTo(minActivationBalance);
  }

  @Override
  protected UInt64 getEffectiveBalanceLimitForValidator(final Validator validator) {
    return miscHelpers.getMaxEffectiveBalance(validator);
  }

  /*
   * <spec function="process_effective_balance_updates" fork="electra">
   * def process_effective_balance_updates(state: BeaconState) -> None:
   *     # Update effective balances with hysteresis
   *     for index, validator in enumerate(state.validators):
   *         balance = state.balances[index]
   *         HYSTERESIS_INCREMENT = uint64(EFFECTIVE_BALANCE_INCREMENT // HYSTERESIS_QUOTIENT)
   *         DOWNWARD_THRESHOLD = HYSTERESIS_INCREMENT * HYSTERESIS_DOWNWARD_MULTIPLIER
   *         UPWARD_THRESHOLD = HYSTERESIS_INCREMENT * HYSTERESIS_UPWARD_MULTIPLIER
   *         # [Modified in Electra:EIP7251]
   *         max_effective_balance = get_max_effective_balance(validator)
   *
   *         if (
   *             balance + DOWNWARD_THRESHOLD < validator.effective_balance
   *             or validator.effective_balance + UPWARD_THRESHOLD < balance
   *         ):
   *             validator.effective_balance = min(balance - balance % EFFECTIVE_BALANCE_INCREMENT, max_effective_balance)
   * </spec>
   */
  @Override
  public void processEffectiveBalanceUpdates(
      final MutableBeaconState state, final List<ValidatorStatus> statuses) {
    // Update effective balances with hysteresis
    final SszMutableList<Validator> validators = state.getValidators();
    final SszUInt64List balances = state.getBalances();
    final UInt64 hysteresisUpwardMultiplier = specConfig.getHysteresisUpwardMultiplier();
    final UInt64 hysteresisDownwardMultiplier = specConfig.getHysteresisDownwardMultiplier();
    final UInt64 hysteresisQuotient = specConfig.getHysteresisQuotient();
    final UInt64 effectiveBalanceIncrement = specConfig.getEffectiveBalanceIncrement();
    for (int index = 0; index < statuses.size(); index++) {
      final ValidatorStatus status = statuses.get(index);
      final UInt64 balance = balances.getElement(index);

      final UInt64 hysteresisIncrement = effectiveBalanceIncrement.dividedBy(hysteresisQuotient);
      final UInt64 currentEffectiveBalance = status.getCurrentEpochEffectiveBalance();
      final Validator validator = validators.get(index);
      final UInt64 maxEffectiveBalance = getEffectiveBalanceLimitForValidator(validator);
      if (shouldDecreaseEffectiveBalance(
              balance, hysteresisIncrement, currentEffectiveBalance, hysteresisDownwardMultiplier)
          || shouldIncreaseEffectiveBalance(
              balance,
              hysteresisIncrement,
              currentEffectiveBalance,
              hysteresisUpwardMultiplier,
              maxEffectiveBalance)) {
        final UInt64 effectiveBalanceLimit = getEffectiveBalanceLimitForValidator(validator);
        final UInt64 newEffectiveBalance =
            effectiveBalanceLimit.min(
                balance.minus(balance.mod(effectiveBalanceIncrement)).min(maxEffectiveBalance));
        BeaconStateCache.getTransitionCaches(state)
            .getProgressiveTotalBalances()
            .onEffectiveBalanceChange(status, newEffectiveBalance);
        validators.set(index, validator.withEffectiveBalance(newEffectiveBalance));
      }
    }
  }

  /*
   * <spec function="apply_pending_deposit" fork="electra">
   * def apply_pending_deposit(state: BeaconState, deposit: PendingDeposit) -> None:
   *     """
   *     Applies ``deposit`` to the ``state``.
   *     """
   *     validator_pubkeys = [v.pubkey for v in state.validators]
   *     if deposit.pubkey not in validator_pubkeys:
   *         # Verify the deposit signature (proof of possession) which is not checked by the deposit contract
   *         if is_valid_deposit_signature(
   *             deposit.pubkey,
   *             deposit.withdrawal_credentials,
   *             deposit.amount,
   *             deposit.signature
   *         ):
   *             add_validator_to_registry(state, deposit.pubkey, deposit.withdrawal_credentials, deposit.amount)
   *     else:
   *         validator_index = ValidatorIndex(validator_pubkeys.index(deposit.pubkey))
   *         increase_balance(state, validator_index, deposit.amount)
   * </spec>
   */
  @Override
  public void applyPendingDeposits(final MutableBeaconState state, final PendingDeposit deposit) {
    validatorsUtil
        .getValidatorIndex(state, deposit.getPublicKey())
        .ifPresentOrElse(
            validatorIndex ->
                beaconStateMutators.increaseBalance(state, validatorIndex, deposit.getAmount()),
            () -> {
              if (isValidPendingDepositSignature(deposit)) {
                beaconStateMutators.addValidatorToRegistry(
                    state,
                    deposit.getPublicKey(),
                    deposit.getWithdrawalCredentials(),
                    deposit.getAmount());
              }
            });
  }

  private boolean isValidPendingDepositSignature(final PendingDeposit deposit) {
    return miscHelpers.isValidDepositSignature(
        deposit.getPublicKey(),
        deposit.getWithdrawalCredentials(),
        deposit.getAmount(),
        deposit.getSignature());
  }

  /*
   * <spec function="process_pending_deposits" fork="electra">
   * def process_pending_deposits(state: BeaconState) -> None:
   *     next_epoch = Epoch(get_current_epoch(state) + 1)
   *     available_for_processing = state.deposit_balance_to_consume + get_activation_exit_churn_limit(state)
   *     processed_amount = 0
   *     next_deposit_index = 0
   *     deposits_to_postpone = []
   *     is_churn_limit_reached = False
   *     finalized_slot = compute_start_slot_at_epoch(state.finalized_checkpoint.epoch)
   *
   *     for deposit in state.pending_deposits:
   *         # Do not process deposit requests if Eth1 bridge deposits are not yet applied.
   *         if (
   *             # Is deposit request
   *             deposit.slot > GENESIS_SLOT and
   *             # There are pending Eth1 bridge deposits
   *             state.eth1_deposit_index < state.deposit_requests_start_index
   *         ):
   *             break
   *
   *         # Check if deposit has been finalized, otherwise, stop processing.
   *         if deposit.slot > finalized_slot:
   *             break
   *
   *         # Check if number of processed deposits has not reached the limit, otherwise, stop processing.
   *         if next_deposit_index >= MAX_PENDING_DEPOSITS_PER_EPOCH:
   *             break
   *
   *         # Read validator state
   *         is_validator_exited = False
   *         is_validator_withdrawn = False
   *         validator_pubkeys = [v.pubkey for v in state.validators]
   *         if deposit.pubkey in validator_pubkeys:
   *             validator = state.validators[ValidatorIndex(validator_pubkeys.index(deposit.pubkey))]
   *             is_validator_exited = validator.exit_epoch < FAR_FUTURE_EPOCH
   *             is_validator_withdrawn = validator.withdrawable_epoch < next_epoch
   *
   *         if is_validator_withdrawn:
   *             # Deposited balance will never become active. Increase balance but do not consume churn
   *             apply_pending_deposit(state, deposit)
   *         elif is_validator_exited:
   *             # Validator is exiting, postpone the deposit until after withdrawable epoch
   *             deposits_to_postpone.append(deposit)
   *         else:
   *             # Check if deposit fits in the churn, otherwise, do no more deposit processing in this epoch.
   *             is_churn_limit_reached = processed_amount + deposit.amount > available_for_processing
   *             if is_churn_limit_reached:
   *                 break
   *
   *             # Consume churn and apply deposit.
   *             processed_amount += deposit.amount
   *             apply_pending_deposit(state, deposit)
   *
   *         # Regardless of how the deposit was handled, we move on in the queue.
   *         next_deposit_index += 1
   *
   *     state.pending_deposits = state.pending_deposits[next_deposit_index:] + deposits_to_postpone
   *
   *     # Accumulate churn only if the churn limit has been hit.
   *     if is_churn_limit_reached:
   *         state.deposit_balance_to_consume = available_for_processing - processed_amount
   *     else:
   *         state.deposit_balance_to_consume = Gwei(0)
   * </spec>
   */
  @Override
  public void processPendingDeposits(final MutableBeaconState state) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);

    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(UInt64.ONE);
    final UInt64 availableForProcessing =
        stateElectra
            .getDepositBalanceToConsume()
            .plus(stateAccessorsElectra.getActivationExitChurnLimit(stateElectra));
    UInt64 processedAmount = UInt64.ZERO;
    int nextDepositIndex = 0;
    final List<PendingDeposit> depositsToPostpone = new ArrayList<>();
    boolean isChurnLimitReached = false;
    final UInt64 finalizedSlot =
        miscHelpers.computeStartSlotAtEpoch(stateElectra.getFinalizedCheckpoint().getEpoch());

    for (final PendingDeposit deposit : stateElectra.getPendingDeposits()) {
      // Do not process deposit requests if Eth1 bridge deposits are not yet applied.
      final boolean isDepositRequest = deposit.getSlot().isGreaterThan(GENESIS_SLOT);
      final boolean hasPendingEth1BridgeDeposits =
          stateElectra
              .getEth1DepositIndex()
              .isLessThan(stateElectra.getDepositRequestsStartIndex());
      if (isDepositRequest && hasPendingEth1BridgeDeposits) {
        break;
      }

      // Check if deposit has been finalized, otherwise stop processing
      if (deposit.getSlot().isGreaterThan(finalizedSlot)) {
        break;
      }

      // Check if number of processed deposits has not reached the limit, otherwise, stop processing
      if (nextDepositIndex
          >= SpecConfigElectra.required(specConfig).getMaxPendingDepositsPerEpoch()) {
        break;
      }

      final Optional<Integer> maybeValidatorIndex =
          validatorsUtil.getValidatorIndex(state, deposit.getPublicKey());
      boolean isValidatorExited = false;
      boolean isValidatorWithdrawn = false;
      if (maybeValidatorIndex.isPresent()) {
        Validator validator = state.getValidators().get(maybeValidatorIndex.get());
        isValidatorExited = validator.getExitEpoch().isLessThan(FAR_FUTURE_EPOCH);
        isValidatorWithdrawn = validator.getWithdrawableEpoch().isLessThan(nextEpoch);
      }

      if (isValidatorWithdrawn) {
        // Deposited balance will never become active. Increase balance but do not consume churn
        applyPendingDeposits(state, deposit);
      } else if (isValidatorExited) {
        // Validator is exiting, postpone the deposit until after withdrawable epoch
        depositsToPostpone.add(deposit);
      } else {
        // Check if deposit fits in the churn, otherwise, do no more deposit processing in this
        // epoch
        isChurnLimitReached =
            processedAmount.plus(deposit.getAmount()).isGreaterThan(availableForProcessing);
        if (isChurnLimitReached) {
          break;
        }
        // Consume churn and apply deposit
        processedAmount = processedAmount.plus(deposit.getAmount());
        applyPendingDeposits(state, deposit);
      }

      // Regardless of how the deposit was handled, we move on in the queue
      nextDepositIndex += 1;
    }

    final SszMutableList<PendingDeposit> pendingDeposits = stateElectra.getPendingDeposits();
    final ArrayList<PendingDeposit> newPendingDeposits = new ArrayList<>();
    IntStream.range(nextDepositIndex, pendingDeposits.size())
        .sorted()
        .forEach(index -> newPendingDeposits.add(pendingDeposits.get(index)));
    newPendingDeposits.addAll(depositsToPostpone);
    stateElectra.setPendingDeposits(
        schemaDefinitionsElectra.getPendingDepositsSchema().createFromElements(newPendingDeposits));

    // Accumulate churn only if the churn limit has been hit
    if (isChurnLimitReached) {
      stateElectra.setDepositBalanceToConsume(availableForProcessing.minusMinZero(processedAmount));
    } else {
      stateElectra.setDepositBalanceToConsume(UInt64.ZERO);
    }
  }

  /*
   * <spec function="process_pending_consolidations" fork="electra">
   * def process_pending_consolidations(state: BeaconState) -> None:
   *     next_epoch = Epoch(get_current_epoch(state) + 1)
   *     next_pending_consolidation = 0
   *     for pending_consolidation in state.pending_consolidations:
   *         source_validator = state.validators[pending_consolidation.source_index]
   *         if source_validator.slashed:
   *             next_pending_consolidation += 1
   *             continue
   *         if source_validator.withdrawable_epoch > next_epoch:
   *             break
   *
   *         # Calculate the consolidated balance
   *         source_effective_balance = min(
   *             state.balances[pending_consolidation.source_index], source_validator.effective_balance)
   *
   *         # Move active balance to target. Excess balance is withdrawable.
   *         decrease_balance(state, pending_consolidation.source_index, source_effective_balance)
   *         increase_balance(state, pending_consolidation.target_index, source_effective_balance)
   *         next_pending_consolidation += 1
   *
   *     state.pending_consolidations = state.pending_consolidations[next_pending_consolidation:]
   * </spec>
   */
  @Override
  public void processPendingConsolidations(final MutableBeaconState state) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    int nextPendingBalanceConsolidation = 0;
    final SszList<PendingConsolidation> pendingConsolidations =
        stateElectra.getPendingConsolidations();
    final UInt64 nextEpoch = stateAccessorsElectra.getCurrentEpoch(state).plus(1L);

    for (final PendingConsolidation pendingConsolidation : pendingConsolidations) {
      final Validator sourceValidator =
          state.getValidators().get(pendingConsolidation.getSourceIndex());
      if (sourceValidator.isSlashed()) {
        nextPendingBalanceConsolidation++;
        continue;
      }
      if (sourceValidator.getWithdrawableEpoch().isGreaterThan(nextEpoch)) {
        break;
      }

      // Calculate the consolidated balance
      final UInt64 sourceEffectiveBalance =
          state
              .getBalances()
              .get(pendingConsolidation.getSourceIndex())
              .get()
              .min(sourceValidator.getEffectiveBalance());
      // Move active balance to target. Excess balance is withdrawable.
      beaconStateMutators.decreaseBalance(
          state, pendingConsolidation.getSourceIndex(), sourceEffectiveBalance);
      beaconStateMutators.increaseBalance(
          state, pendingConsolidation.getTargetIndex(), sourceEffectiveBalance);

      nextPendingBalanceConsolidation++;
    }
    if (pendingConsolidations.size() <= nextPendingBalanceConsolidation) {
      stateElectra.setPendingConsolidations(
          schemaDefinitionsElectra.getPendingConsolidationsSchema().createFromElements(List.of()));
    } else {
      final List<PendingConsolidation> newList =
          pendingConsolidations
              .asList()
              .subList(nextPendingBalanceConsolidation, pendingConsolidations.size());
      stateElectra.setPendingConsolidations(
          schemaDefinitionsElectra.getPendingConsolidationsSchema().createFromElements(newList));
    }
  }

  /*
   * <spec function="process_slashings" fork="electra">
   * def process_slashings(state: BeaconState) -> None:
   *     epoch = get_current_epoch(state)
   *     total_balance = get_total_active_balance(state)
   *     adjusted_total_slashing_balance = min(
   *         sum(state.slashings) * PROPORTIONAL_SLASHING_MULTIPLIER_BELLATRIX,
   *         total_balance
   *     )
   *     increment = EFFECTIVE_BALANCE_INCREMENT  # Factored out from total balance to avoid uint64 overflow
   *     penalty_per_effective_balance_increment = adjusted_total_slashing_balance // (total_balance // increment)
   *     for index, validator in enumerate(state.validators):
   *         if validator.slashed and epoch + EPOCHS_PER_SLASHINGS_VECTOR // 2 == validator.withdrawable_epoch:
   *             effective_balance_increments = validator.effective_balance // increment
   *             # [Modified in Electra:EIP7251]
   *             penalty = penalty_per_effective_balance_increment * effective_balance_increments
   *             decrease_balance(state, ValidatorIndex(index), penalty)
   * </spec>
   */
  @Override
  public void processSlashings(
      final MutableBeaconState state, final ValidatorStatuses validatorStatuses) {
    final UInt64 totalBalance =
        validatorStatuses.getTotalBalances().getCurrentEpochActiveValidators();
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 adjustedTotalSlashingBalance =
        state
            .getSlashings()
            .streamUnboxed()
            .reduce(ZERO, UInt64::plus)
            .times(getProportionalSlashingMultiplier())
            .min(totalBalance);

    final List<ValidatorStatus> validatorStatusList = validatorStatuses.getStatuses();
    final int halfEpochsPerSlashingsVector = specConfig.getEpochsPerSlashingsVector() / 2;

    final UInt64 increment = specConfig.getEffectiveBalanceIncrement();
    final UInt64 penaltyPerEffectiveBalanceIncrement =
        adjustedTotalSlashingBalance.dividedBy(totalBalance.dividedBy(increment));
    for (int index = 0; index < validatorStatusList.size(); index++) {
      final ValidatorStatus status = validatorStatusList.get(index);
      if (status.isSlashed()
          && epoch.plus(halfEpochsPerSlashingsVector).equals(status.getWithdrawableEpoch())) {

        // EIP-7251
        final UInt64 effectiveBalanceIncrements =
            status.getCurrentEpochEffectiveBalance().dividedBy(increment);
        final UInt64 penalty =
            penaltyPerEffectiveBalanceIncrement.times(effectiveBalanceIncrements);
        beaconStateMutators.decreaseBalance(state, index, penalty);
      }
    }
  }

  @Override
  protected boolean shouldCheckNewValidatorsDuringEpochProcessing() {
    return true;
  }
}
