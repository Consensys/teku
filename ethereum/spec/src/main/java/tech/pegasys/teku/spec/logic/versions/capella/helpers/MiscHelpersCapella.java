/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.capella.helpers;

import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.ETH1_ADDRESS_WITHDRAWAL_PREFIX;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.MiscHelpersBellatrix;

public class MiscHelpersCapella extends MiscHelpersBellatrix {

  public MiscHelpersCapella(final SpecConfig specConfig) {
    super(specConfig);
  }

  /**
   * Implementation of <b>has_eth1_withdrawal_credential</b> Capella Helper function. <br>
   * Checks if validator has a 0x01 prefixed "eth1" withdrawal credential.
   *
   * @param validator the validator being checked
   * @return true if the validator has an "eth1" withdrawal credential, false otherwise
   */
  public boolean hasEth1WithdrawalCredential(final Validator validator) {
    return validator.getWithdrawalCredentials().slice(0, 1).equals(ETH1_ADDRESS_WITHDRAWAL_PREFIX);
  }

  /**
   * Implementation of <b>is_fully_withdrawable_validator</b> Capella helper function. <br>
   * Checks if validator is fully withdrawable.
   *
   * @param validator the validator being checked
   * @param balance the validator's balance
   * @param epoch the current epoch
   * @return true if the validator can fully withdraw their funds, false otherwise
   */
  public boolean isFullyWithdrawableValidator(
      final Validator validator, final UInt64 balance, final UInt64 epoch) {
    return hasEth1WithdrawalCredential(validator)
        && validator.getWithdrawableEpoch().isLessThanOrEqualTo(epoch)
        && balance.isGreaterThan(UInt64.ZERO);
  }

  /**
   * Implementation of <b>is_partially_withdrawable_validator</b> Capella helper function. <br>
   * Checks if validator is partially withdrawable.
   *
   * @param validator the validator being checked
   * @param balance the validator's balance
   * @return true if the validator can partially withdraw their funds, false otherwise
   */
  public boolean isPartiallyWithdrawableValidator(final Validator validator, final UInt64 balance) {
    final UInt64 maxEffectiveBalance = specConfig.getMaxEffectiveBalance();
    final boolean hasMaxEffectiveBalance =
        validator.getEffectiveBalance().equals(maxEffectiveBalance);
    final boolean hasExcessBalance = balance.isGreaterThan(maxEffectiveBalance);

    return hasEth1WithdrawalCredential(validator) && hasMaxEffectiveBalance && hasExcessBalance;
  }
}
