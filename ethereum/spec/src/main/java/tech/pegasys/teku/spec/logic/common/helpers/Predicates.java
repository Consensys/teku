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

package tech.pegasys.teku.spec.logic.common.helpers;

import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.ETH1_ADDRESS_WITHDRAWAL_BYTE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;

public class Predicates {

  private final SpecConfig specConfig;

  public Predicates(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  /**
   * Check if (this) validator is active in the given epoch.
   *
   * @param validator The validator under consideration.
   * @param epoch - The epoch under consideration.
   * @return A boolean indicating if the validator is active.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_active_validator">is_active_validator</a>
   */
  public boolean isActiveValidator(Validator validator, UInt64 epoch) {
    return isActiveValidator(validator.getActivationEpoch(), validator.getExitEpoch(), epoch);
  }

  public boolean isActiveValidator(UInt64 activationEpoch, UInt64 exitEpoch, UInt64 epoch) {
    return activationEpoch.compareTo(epoch) <= 0 && epoch.compareTo(exitEpoch) < 0;
  }

  public boolean isValidMerkleBranch(
      Bytes32 leaf, SszBytes32Vector branch, int depth, int index, Bytes32 root) {
    final Sha256 sha256 = new Sha256();
    Bytes32 value = leaf;
    for (int i = 0; i < depth; i++) {
      if ((index & 1) == 1) {
        value = sha256.wrappedDigest(branch.getElement(i), value);
      } else {
        value = sha256.wrappedDigest(value, branch.getElement(i));
      }
      index >>>= 1;
    }
    return value.equals(root);
  }

  /**
   * Determines if a validator has a balance that can be slashed
   *
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_slashable_validator">is_slashable_validator</a>
   */
  public boolean isSlashableValidator(Validator validator, UInt64 epoch) {
    return !validator.isSlashed()
        && (validator.getActivationEpoch().compareTo(epoch) <= 0
            && epoch.compareTo(validator.getWithdrawableEpoch()) < 0);
  }

  /**
   * Implementation of <b>has_eth1_withdrawal_credential</b> Capella Helper function. <br>
   * Checks if validator has a 0x01 prefixed "eth1" withdrawal credential.
   *
   * @param validator the validator being checked
   * @return true if the validator has an "eth1" withdrawal credential, false otherwise
   */
  public boolean hasEth1WithdrawalCredential(final Validator validator) {
    return validator.getWithdrawalCredentials().get(0) == ETH1_ADDRESS_WITHDRAWAL_BYTE;
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
        && isFullyWithdrawableValidatorEth1CredentialsChecked(validator, balance, epoch);
  }

  public boolean isFullyWithdrawableValidatorEth1CredentialsChecked(
      final Validator validator, final UInt64 balance, final UInt64 epoch) {
    return validator.getWithdrawableEpoch().isLessThanOrEqualTo(epoch)
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
    return hasEth1WithdrawalCredential(validator)
        && isPartiallyWithdrawableValidatorEth1CredentialsChecked(validator, balance);
  }

  public boolean isPartiallyWithdrawableValidatorEth1CredentialsChecked(
      final Validator validator, final UInt64 balance) {
    final UInt64 maxEffectiveBalance = specConfig.getMaxEffectiveBalance();
    final boolean hasMaxEffectiveBalance =
        validator.getEffectiveBalance().equals(maxEffectiveBalance);
    final boolean hasExcessBalance = balance.isGreaterThan(maxEffectiveBalance);

    return hasMaxEffectiveBalance && hasExcessBalance;
  }
}
