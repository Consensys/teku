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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateMutatorsBellatrix;

public class BeaconStateMutatorsElectra extends BeaconStateMutatorsBellatrix {
  private final BeaconStateAccessorsElectra stateAccessorsElectra;
  private final MiscHelpersElectra miscHelpersElectra;

  private final SpecConfigElectra specConfigElectra;

  public static BeaconStateMutatorsElectra required(final BeaconStateMutators beaconStateMutators) {
    checkArgument(
        beaconStateMutators instanceof BeaconStateMutatorsElectra,
        "Expected %s but it was %s",
        BeaconStateMutatorsElectra.class,
        beaconStateMutators.getClass());
    return (BeaconStateMutatorsElectra) beaconStateMutators;
  }

  public BeaconStateMutatorsElectra(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    super(SpecConfigBellatrix.required(specConfig), miscHelpers, beaconStateAccessors);
    this.stateAccessorsElectra = BeaconStateAccessorsElectra.required(beaconStateAccessors);
    this.miscHelpersElectra = MiscHelpersElectra.required(miscHelpers);
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
  }

  /**
   * compute_exit_epoch_and_update_churn
   *
   * @param state mutable beacon state
   * @param exitBalance exit balance
   * @return earliest exit epoch (updated in state)
   */
  public UInt64 computeExitEpochAndUpdateChurn(
      final MutableBeaconStateElectra state, final UInt64 exitBalance) {
    final UInt64 earliestExitEpoch =
        miscHelpers.computeActivationExitEpoch(stateAccessorsElectra.getCurrentEpoch(state));
    final UInt64 perEpochChurn = stateAccessorsElectra.getActivationExitChurnLimit(state);

    if (state.getEarliestExitEpoch().isLessThan(earliestExitEpoch)) {
      state.setEarliestExitEpoch(earliestExitEpoch);
      state.setExitBalanceToConsume(perEpochChurn);
    }
    final UInt64 exitBalanceToConsume = state.getExitBalanceToConsume();
    if (exitBalance.isLessThanOrEqualTo(exitBalanceToConsume)) {
      state.setExitBalanceToConsume(exitBalanceToConsume.minusMinZero(exitBalance));
    } else {
      final UInt64 balanceToProcess = exitBalance.minusMinZero(state.getExitBalanceToConsume());
      final UInt64 additionalEpochs = balanceToProcess.dividedBy(perEpochChurn);
      final UInt64 remainder = balanceToProcess.mod(perEpochChurn);
      state.setEarliestExitEpoch(additionalEpochs.increment());
      state.setExitBalanceToConsume(perEpochChurn.minusMinZero(remainder));
    }
    return state.getEarliestExitEpoch();
  }

  /**
   * initiate_validator_exit
   *
   * @param state
   * @param index
   * @param validatorExitContextSupplier
   */
  @Override
  public void initiateValidatorExit(
      final MutableBeaconState state,
      final int index,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final Validator validator = state.getValidators().get(index);
    // Return if validator already initiated exit
    if (!validator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }

    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);

    final ValidatorExitContext validatorExitContext = validatorExitContextSupplier.get();

    if (validatorExitContext.getExitQueueChurn().compareTo(validatorExitContext.getChurnLimit())
        >= 0) {
      validatorExitContext.setExitQueueEpoch(validatorExitContext.getExitQueueEpoch().increment());
      validatorExitContext.setExitQueueChurn(UInt64.ONE);
    } else {
      validatorExitContext.setExitQueueChurn(validatorExitContext.getExitQueueChurn().increment());
    }

    final UInt64 exitQueueEpoch =
        computeExitEpochAndUpdateChurn(stateElectra, validator.getEffectiveBalance());

    // Set validator exit epoch and withdrawable epoch
    stateElectra
        .getValidators()
        .set(
            index,
            validator
                .withExitEpoch(exitQueueEpoch)
                .withWithdrawableEpoch(
                    exitQueueEpoch.plus(specConfig.getMinValidatorWithdrawabilityDelay())));
  }

  /**
   * compute_consolidation_epoch_and_update_churn
   *
   * @param state
   * @param consolidationBalance
   * @return
   */
  public UInt64 computeConsolidationEpochAndUpdateChurn(
      final MutableBeaconState state, final UInt64 consolidationBalance) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    final UInt64 computedActivationExitEpoch = miscHelpersElectra.computeActivationExitEpoch(epoch);
    final UInt64 earliestConsolidationEpoch =
        stateElectra.getEarliestConsolidationEpoch().max(computedActivationExitEpoch);
    final UInt64 perEpochConsolidationChurn =
        stateAccessorsElectra.getConsolidationChurnLimit(stateElectra);

    final UInt64 consolidationBalanceToConsume =
        stateElectra.getEarliestConsolidationEpoch().isLessThan(earliestConsolidationEpoch)
            ? perEpochConsolidationChurn
            : stateElectra.getConsolidationBalanceToConsume();

    if (consolidationBalance.isGreaterThan(consolidationBalanceToConsume)) {
      final UInt64 balanceToProcess =
          consolidationBalance.minusMinZero(consolidationBalanceToConsume);
      final UInt64 additionalEpochs =
          balanceToProcess.decrement().dividedBy(perEpochConsolidationChurn.increment());
      stateElectra.setConsolidationBalanceToConsume(
          consolidationBalanceToConsume.plus(
              additionalEpochs
                  .times(perEpochConsolidationChurn)
                  .minusMinZero(consolidationBalance)));
      stateElectra.setEarliestConsolidationEpoch(earliestConsolidationEpoch.plus(additionalEpochs));
    } else {
      stateElectra.setConsolidationBalanceToConsume(
          consolidationBalanceToConsume.minusMinZero(consolidationBalance));
      stateElectra.setEarliestConsolidationEpoch(earliestConsolidationEpoch);
    }

    return stateElectra.getEarliestConsolidationEpoch();
  }

  @Override
  protected int getWhistleblowerRewardQuotient() {
    return specConfigElectra.getWhistleblowerRewardQuotientElectra();
  }

  @Override
  protected int getMinSlashingPenaltyQuotient() {
    return specConfigElectra.getMinSlashingPenaltyQuotientElectra();
  }
}
