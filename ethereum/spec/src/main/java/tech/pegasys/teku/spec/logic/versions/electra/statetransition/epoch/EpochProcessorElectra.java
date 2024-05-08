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

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.capella.statetransition.epoch.EpochProcessorCapella;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class EpochProcessorElectra extends EpochProcessorCapella {

  private final UInt64 minActivationBalance;
  private final BeaconStateAccessorsElectra stateAccessorsElectra;
  private final BeaconStateMutatorsElectra stateMutatorsElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;

  public EpochProcessorElectra(
      final SpecConfigCapella specConfig,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitions schemaDefinitions) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions);
    this.minActivationBalance =
        specConfig.toVersionElectra().orElseThrow().getMinActivationBalance();
    this.stateAccessorsElectra = BeaconStateAccessorsElectra.required(beaconStateAccessors);
    this.stateMutatorsElectra = BeaconStateMutatorsElectra.required(beaconStateMutators);
    this.schemaDefinitionsElectra = SchemaDefinitionsElectra.required(schemaDefinitions);
  }

  /**
   * process_registry_updates
   *
   * @param state
   * @param statuses
   * @throws EpochProcessingException
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

  /**
   * is_eligible_for_activation_queue
   *
   * @param status - Validator status
   * @return
   */
  @Override
  protected boolean isEligibleForActivationQueue(final ValidatorStatus status) {
    return !status.isActiveInCurrentEpoch()
        && status.getCurrentEpochEffectiveBalance().isGreaterThanOrEqualTo(minActivationBalance);
  }

  @Override
  protected UInt64 getEffectiveBalanceLimitForValidator(final Validator validator) {
    return stateAccessorsElectra.getValidatorMaxEffectiveBalance(validator);
  }

  // process_effective_balance_updates
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
    for (int index = 0; index < validators.size(); index++) {
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

  /**
   * process_pending_balance_deposits
   *
   * @param state
   */
  @Override
  public void processPendingBalanceDeposits(final MutableBeaconState state) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    final UInt64 availableForProcessing =
        stateElectra
            .getDepositBalanceToConsume()
            .plus(stateAccessorsElectra.getActivationExitChurnLimit(stateElectra));

    UInt64 processedAmount = UInt64.ZERO;
    int nextDepositIndex = 0;

    final SszList<PendingBalanceDeposit> pendingBalanceDeposits =
        stateElectra.getPendingBalanceDeposits();
    for (final PendingBalanceDeposit deposit : pendingBalanceDeposits) {
      if (processedAmount.plus(deposit.getAmount()).isGreaterThan(availableForProcessing)) {
        break;
      }
      stateMutatorsElectra.increaseBalance(state, deposit.getIndex(), deposit.getAmount());
      processedAmount = processedAmount.plus(deposit.getAmount());
      nextDepositIndex++;
    }

    if (!pendingBalanceDeposits.isEmpty()) {
      final List<PendingBalanceDeposit> newList =
          pendingBalanceDeposits.asList().subList(nextDepositIndex, pendingBalanceDeposits.size());
      stateElectra.setPendingBalanceDeposits(
          schemaDefinitionsElectra.getPendingBalanceDepositsSchema().createFromElements(newList));
      stateElectra.setDepositBalanceToConsume(availableForProcessing.minusMinZero(processedAmount));
    }
    if (stateElectra.getPendingBalanceDeposits().isEmpty()) {
      stateElectra.setDepositBalanceToConsume(UInt64.ZERO);
    } else {
      stateElectra.setDepositBalanceToConsume(availableForProcessing.minusMinZero(processedAmount));
    }
  }

  /**
   * process_pending_consolidations
   *
   * @param state
   */
  @Override
  public void processPendingConsolidations(final MutableBeaconState state) {

    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    int nextPendingBalanceConsolidation = 0;
    final SszList<PendingConsolidation> pendingConsolidations =
        stateElectra.getPendingConsolidations();
    final UInt64 currentEpoch = stateAccessorsElectra.getCurrentEpoch(state);

    for (final PendingConsolidation pendingConsolidation : pendingConsolidations) {
      final Validator sourceValidator =
          state.getValidators().get(pendingConsolidation.getSourceIndex());
      if (sourceValidator.isSlashed()) {
        nextPendingBalanceConsolidation++;
        continue;
      }
      if (sourceValidator.getWithdrawableEpoch().isGreaterThan(currentEpoch)) {
        break;
      }

      stateMutatorsElectra.switchToCompoundingValidator(
          stateElectra, pendingConsolidation.getTargetIndex());
      final UInt64 activeBalance =
          stateAccessorsElectra.getActiveBalance(state, pendingConsolidation.getSourceIndex());
      beaconStateMutators.decreaseBalance(
          state, pendingConsolidation.getSourceIndex(), activeBalance);
      beaconStateMutators.increaseBalance(
          state, pendingConsolidation.getTargetIndex(), activeBalance);

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
}
