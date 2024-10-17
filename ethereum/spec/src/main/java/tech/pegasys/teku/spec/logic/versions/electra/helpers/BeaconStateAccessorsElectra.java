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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.BeaconStateAccessorsDeneb;

public class BeaconStateAccessorsElectra extends BeaconStateAccessorsDeneb {

  private final SpecConfigElectra configElectra;
  protected PredicatesElectra predicatesElectra;

  public BeaconStateAccessorsElectra(
      final SpecConfig config,
      final PredicatesElectra predicatesElectra,
      final MiscHelpersElectra miscHelpers) {
    super(SpecConfigDeneb.required(config), predicatesElectra, miscHelpers);
    configElectra = config.toVersionElectra().orElseThrow();
    this.predicatesElectra = predicatesElectra;
  }

  /**
   * get_activation_exit_churn_limit
   *
   * @param state - the state to use to get the churn limit from
   * @return Return the churn limit for the current epoch dedicated to activations and exits.
   */
  public UInt64 getActivationExitChurnLimit(final BeaconStateElectra state) {
    return getBalanceChurnLimit(state).min(configElectra.getMaxPerEpochActivationExitChurnLimit());
  }

  /**
   * get_active_balance
   *
   * @param state The state to get the effective balance from
   * @param validatorIndex the index of the validator
   */
  public UInt64 getActiveBalance(final BeaconState state, final int validatorIndex) {
    final Validator validator = state.getValidators().get(validatorIndex);
    final UInt64 maxEffectiveBalance = miscHelpers.getMaxEffectiveBalance(validator);
    final UInt64 validatorBalance = state.getBalances().get(validatorIndex).get();
    return validatorBalance.min(maxEffectiveBalance);
  }

  /**
   * get_pending_balance_to_withdraw
   *
   * @param state The state
   * @param validatorIndex The index of the validator
   * @return The sum of the withdrawal amounts for the validator in the partial withdrawal queue.
   */
  public UInt64 getPendingBalanceToWithdraw(
      final BeaconStateElectra state, final int validatorIndex) {
    final List<PendingPartialWithdrawal> partialWithdrawals =
        state.getPendingPartialWithdrawals().asList();
    return partialWithdrawals.stream()
        .filter(z -> z.getIndex() == validatorIndex)
        .map(PendingPartialWithdrawal::getAmount)
        .reduce(UInt64.ZERO, UInt64::plus);
  }

  /**
   * get_balance_churn_limit
   *
   * @param state the state to read active balance from
   * @return Return the churn limit for the current epoch.
   */
  public UInt64 getBalanceChurnLimit(final BeaconStateElectra state) {
    final UInt64 churn =
        configElectra
            .getMinPerEpochChurnLimitElectra()
            .max(getTotalActiveBalance(state).dividedBy(configElectra.getChurnLimitQuotient()));
    return churn.minusMinZero(churn.mod(configElectra.getEffectiveBalanceIncrement()));
  }

  /**
   * get_consolidation_churn_limit
   *
   * @param state state to read churn limits from
   */
  public UInt64 getConsolidationChurnLimit(final BeaconStateElectra state) {
    return getBalanceChurnLimit(state).minusMinZero(getActivationExitChurnLimit(state));
  }

  public static BeaconStateAccessorsElectra required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsElectra,
        "Expected %s but it was %s",
        BeaconStateAccessorsElectra.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsElectra) beaconStateAccessors;
  }

  @Override
  public IntList getNextSyncCommitteeIndices(final BeaconState state) {
    return getNextSyncCommitteeIndices(state, configElectra.getMaxEffectiveBalanceElectra());
  }
}
