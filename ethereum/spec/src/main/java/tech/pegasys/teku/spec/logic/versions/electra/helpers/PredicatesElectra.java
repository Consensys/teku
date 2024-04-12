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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class PredicatesElectra extends Predicates {
  private final SpecConfigElectra specConfigElectra;

  public PredicatesElectra(SpecConfig specConfig) {
    super(specConfig);
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
  }

  @Override
  public boolean isPartiallyWithdrawableValidator(final Validator validator, final UInt64 balance) {
    if (hasEth1WithdrawalCredential(validator)) {
      return isPartiallyWithdrawableValidatorEth1CredentialsChecked(validator, balance);
    }
    if (hasCompoundingWithdrawalCredential(validator)) {
      return isPartiallyWithdrawableValidatorCompoundingCredentialChecked(validator, balance);
    }

    return false;
  }

  private boolean isPartiallyWithdrawableValidatorCompoundingCredentialChecked(
      final Validator validator, final UInt64 balance) {
    final UInt64 maxEffectiveBalance = specConfigElectra.getMaxEffectiveBalanceElectra();
    final boolean hasMaxEffectiveBalance =
        validator.getEffectiveBalance().equals(maxEffectiveBalance);
    final boolean hasExcessBalance = balance.isGreaterThan(maxEffectiveBalance);

    return hasMaxEffectiveBalance && hasExcessBalance;
  }

  protected boolean hasCompoundingWithdrawalCredential(final Validator validator) {
    return isCompoundingWithdrawalCredential(validator.getWithdrawalCredentials());
  }

  protected boolean isCompoundingWithdrawalCredential(final Bytes32 withdrawalCredentials) {
    return withdrawalCredentials.get(0) == COMPOUNDING_WITHDRAWAL_BYTE;
  }
}
