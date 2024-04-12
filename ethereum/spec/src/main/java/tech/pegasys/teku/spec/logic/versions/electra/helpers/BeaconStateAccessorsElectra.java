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
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.BeaconStateAccessorsDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public class BeaconStateAccessorsElectra extends BeaconStateAccessorsDeneb {

  private final UInt64 maxEffectiveBalanceElectra;
  private final UInt64 minActivationBalance;

  protected PredicatesElectra predicatesElectra;

  public BeaconStateAccessorsElectra(
      final SpecConfigDeneb config,
      final PredicatesElectra predicatesElectra,
      final MiscHelpersDeneb miscHelpers) {
    super(config, predicatesElectra, miscHelpers);
    this.maxEffectiveBalanceElectra =
        config.toVersionElectra().orElseThrow().getMaxEffectiveBalanceElectra();
    this.minActivationBalance = config.toVersionElectra().orElseThrow().getMinActivationBalance();
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
        ? maxEffectiveBalanceElectra
        : minActivationBalance;
  }
}
