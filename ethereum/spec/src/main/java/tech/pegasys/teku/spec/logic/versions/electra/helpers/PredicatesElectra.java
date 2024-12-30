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

import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.COMPOUNDING_WITHDRAWAL_BYTE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class PredicatesElectra extends Predicates {
  private final SpecConfigElectra configElectra;

  public PredicatesElectra(final SpecConfig specConfig) {
    super(specConfig);
    this.configElectra = SpecConfigElectra.required(specConfig);
  }

  public static PredicatesElectra required(final Predicates predicates) {
    return predicates
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Electra predicates but got "
                        + predicates.getClass().getSimpleName()));
  }

  @Override
  public Optional<PredicatesElectra> toVersionElectra() {
    return Optional.of(this);
  }

  /*
   * <spec function="is_partially_withdrawable_validator" fork="electra">
   * def is_partially_withdrawable_validator(validator: Validator, balance: Gwei) -> bool:
   *     """
   *     Check if ``validator`` is partially withdrawable.
   *     """
   *     max_effective_balance = get_max_effective_balance(validator)
   *     has_max_effective_balance = validator.effective_balance == max_effective_balance  # [Modified in Electra:EIP7251]
   *     has_excess_balance = balance > max_effective_balance  # [Modified in Electra:EIP7251]
   *     return (
   *         has_execution_withdrawal_credential(validator)  # [Modified in Electra:EIP7251]
   *         and has_max_effective_balance
   *         and has_excess_balance
   *     )
   * </spec>
   */
  @Override
  public boolean isPartiallyWithdrawableValidator(final Validator validator, final UInt64 balance) {
    return hasExecutionWithdrawalCredential(validator)
        && isPartiallyWithdrawableValidatorEth1CredentialsChecked(validator, balance);
  }

  @Override
  public boolean isPartiallyWithdrawableValidatorEth1CredentialsChecked(
      final Validator validator, final UInt64 balance) {
    final UInt64 maxEffectiveBalance =
        hasCompoundingWithdrawalCredential(validator)
            ? configElectra.getMaxEffectiveBalanceElectra()
            : configElectra.getMinActivationBalance();
    final boolean hasMaxEffectiveBalance =
        validator.getEffectiveBalance().equals(maxEffectiveBalance);
    final boolean hasExcessBalance = balance.isGreaterThan(maxEffectiveBalance);

    return hasMaxEffectiveBalance && hasExcessBalance;
  }

  /*
   * <spec function="is_fully_withdrawable_validator" fork="electra">
   * def is_fully_withdrawable_validator(validator: Validator, balance: Gwei, epoch: Epoch) -> bool:
   *     """
   *     Check if ``validator`` is fully withdrawable.
   *     """
   *     return (
   *         has_execution_withdrawal_credential(validator)  # [Modified in Electra:EIP7251]
   *         and validator.withdrawable_epoch <= epoch
   *         and balance > 0
   *     )
   * </spec>
   */
  @Override
  public boolean isFullyWithdrawableValidator(
      final Validator validator, final UInt64 balance, final UInt64 epoch) {
    return hasExecutionWithdrawalCredential(validator)
        && isFullyWithdrawableValidatorCredentialsChecked(validator, balance, epoch);
  }

  /*
   * <spec function="has_execution_withdrawal_credential" fork="electra">
   * def has_execution_withdrawal_credential(validator: Validator) -> bool:
   *     """
   *     Check if ``validator`` has a 0x01 or 0x02 prefixed withdrawal credential.
   *     """
   *     return has_compounding_withdrawal_credential(validator) or has_eth1_withdrawal_credential(validator)
   * </spec>
   */
  @Override
  public boolean hasExecutionWithdrawalCredential(final Validator validator) {
    return hasEth1WithdrawalCredential(validator) || hasCompoundingWithdrawalCredential(validator);
  }

  /*
   * <spec function="has_compounding_withdrawal_credential" fork="electra">
   * def has_compounding_withdrawal_credential(validator: Validator) -> bool:
   *     """
   *     Check if ``validator`` has an 0x02 prefixed "compounding" withdrawal credential.
   *     """
   *     return is_compounding_withdrawal_credential(validator.withdrawal_credentials)
   * </spec>
   */
  public boolean hasCompoundingWithdrawalCredential(final Validator validator) {
    return isCompoundingWithdrawalCredential(validator.getWithdrawalCredentials());
  }

  /*
   * <spec function="is_compounding_withdrawal_credential" fork="electra">
   * def is_compounding_withdrawal_credential(withdrawal_credentials: Bytes32) -> bool:
   *     return withdrawal_credentials[:1] == COMPOUNDING_WITHDRAWAL_PREFIX
   * </spec>
   */
  public boolean isCompoundingWithdrawalCredential(final Bytes32 withdrawalCredentials) {
    return withdrawalCredentials.get(0) == COMPOUNDING_WITHDRAWAL_BYTE;
  }
}
