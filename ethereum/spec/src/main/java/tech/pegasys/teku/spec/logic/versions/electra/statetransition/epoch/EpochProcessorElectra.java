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
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.state.Validator;
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
import tech.pegasys.teku.spec.logic.versions.bellatrix.statetransition.epoch.EpochProcessorBellatrix;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class EpochProcessorElectra extends EpochProcessorBellatrix {

  private final UInt64 minActivationBalance;
  private final SchemaDefinitionsElectra definitionsElectra;
  private final BeaconStateAccessorsElectra stateAccessorsElectra;

  private final BeaconStateMutatorsElectra stateMutatorsElectra;
  private final SchemaDefinitionsElectra schemaDefinitionsElectra;

  public EpochProcessorElectra(
      final SpecConfigBellatrix specConfig,
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
    this.definitionsElectra = SchemaDefinitionsElectra.required(schemaDefinitions);
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
        } else {
          // activate all eligible validators
          final Validator validator = validators.get(index);
          if (isEligibleForActivation(finalizedEpoch, validator)) {
            state.getValidators().update(index, v -> v.withActivationEpoch(activationEpoch));
          }
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
   * queue_entire_balance_and_reset_validator
   *
   * @param state beaconState
   * @param index validatorIndex
   */
  protected void queueEntireBalanceAndResetValidator(
      final MutableBeaconStateElectra state, final int index) {
    final UInt64 balance = state.getBalances().getElement(index);
    state.getBalances().set(index, SszUInt64.ZERO);
    state
        .getValidators()
        .update(
            index,
            validator ->
                validator.withActivationEpoch(FAR_FUTURE_EPOCH).withEffectiveBalance(UInt64.ZERO));
    final SszMutableList<PendingBalanceDeposit> pendingDeposits =
        state.getPendingBalanceDeposits().createWritableCopy();
    final PendingBalanceDeposit deposit =
        definitionsElectra
            .getPendingBalanceDepositSchema()
            .create(SszUInt64.of(UInt64.valueOf(index)), SszUInt64.of(balance));
    pendingDeposits.append(deposit);
    state.setPendingBalanceDeposits(pendingDeposits);
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
    for (int i = 0; i < pendingBalanceDeposits.size(); i++) {
      final PendingBalanceDeposit deposit = pendingBalanceDeposits.get(i);
      if (processedAmount.plus(deposit.getAmount()).isGreaterThan(availableForProcessing)) {
        break;
      }
      stateMutatorsElectra.increaseBalance(state, deposit.getIndex(), deposit.getAmount());
      processedAmount = processedAmount.plus(deposit.getAmount());
      nextDepositIndex++;
    }

    if (pendingBalanceDeposits.size() >= nextDepositIndex) {
      stateElectra.setPendingBalanceDeposits(
          schemaDefinitionsElectra.getPendingBalanceDepositsSchema().createFromElements(List.of()));
      stateElectra.setDepositBalanceToConsume(UInt64.ZERO);
    } else {
      final List<PendingBalanceDeposit> newList =
          pendingBalanceDeposits
              .asList()
              .subList(nextDepositIndex, pendingBalanceDeposits.size() - 1);
      stateElectra.setPendingBalanceDeposits(
          schemaDefinitionsElectra.getPendingBalanceDepositsSchema().createFromElements(newList));
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

    for (int i = 0; i < pendingConsolidations.size(); i++) {
      final PendingConsolidation pendingConsolidation = pendingConsolidations.get(i);
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
    if (pendingConsolidations.size() >= nextPendingBalanceConsolidation) {
      stateElectra.setPendingConsolidations(
          schemaDefinitionsElectra.getPendingConsolidationsSchema().createFromElements(List.of()));
    } else {
      final List<PendingConsolidation> newList =
          pendingConsolidations
              .asList()
              .subList(nextPendingBalanceConsolidation, pendingConsolidations.size() - 1);
      stateElectra.setPendingConsolidations(
          schemaDefinitionsElectra.getPendingConsolidationsSchema().createFromElements(newList));
    }
  }
}
