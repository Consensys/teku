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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.BeaconStateAccessorsDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public class BeaconStateAccessorsElectra extends BeaconStateAccessorsDeneb {

  private final SpecConfigElectra configElectra;
  protected PredicatesElectra predicatesElectra;

  public BeaconStateAccessorsElectra(
      final SpecConfig config,
      final PredicatesElectra predicatesElectra,
      final MiscHelpersDeneb miscHelpers) {
    super(SpecConfigDeneb.required(config), predicatesElectra, miscHelpers);
    configElectra = config.toVersionElectra().orElseThrow();
    this.predicatesElectra = predicatesElectra;
  }

  /**
   * implements get_validator_max_effective_balance state accessor
   *
   * @param validator - a validator from a state.
   * @return the max effective balance for the specified validator based on its withdrawal
   *     credentials.
   */
  public UInt64 getValidatorMaxEffectiveBalance(final Validator validator) {
    return predicatesElectra.hasCompoundingWithdrawalCredential(validator)
        ? configElectra.getMaxEffectiveBalanceElectra()
        : configElectra.getMinActivationBalance();
  }

  /**
   * get_activation_exit_churn_limit
   *
   * @param state - the state to use to get the churn limit from
   * @return Return the churn limit for the current epoch dedicated to activations and exits.
   */
  public UInt64 getActivationExitChurnLimit(final BeaconStateElectra state) {
    return getChurnLimit(state).min(configElectra.getMaxPerEpochActivationExitChurnLimit());
  }

  /**
   * get_churn_limit
   *
   * @param state the state to read active balance from
   * @return Return the churn limit for the current epoch.
   */
  public UInt64 getChurnLimit(final BeaconStateElectra state) {
    final UInt64 churn =
        configElectra
            .getMinPerEpochChurnLimitElectra()
            .max(getTotalActiveBalance(state).dividedBy(configElectra.getChurnLimitQuotient()));
    return churn.minusMinZero(churn.dividedBy(configElectra.getEffectiveBalanceIncrement()));
  }

  /**
   * get_consolidation_churn_limit
   *
   * @param state state to read churn limits from
   * @return
   */
  public UInt64 getConsolidationChurnLimit(final BeaconStateElectra state) {
    return getChurnLimit(state).minusMinZero(getActivationExitChurnLimit(state));
  }
}
