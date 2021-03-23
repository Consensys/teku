/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

public class ValidatorsUtil {

  /**
   * Check if validator is eligible for activation.
   *
   * @param state the beacon state
   * @param validator the validator
   * @return true if the validator is eligible for activation
   */
  public boolean isEligibleForActivation(BeaconState state, Validator validator) {
    return validator
                .getActivation_eligibility_epoch()
                .compareTo(state.getFinalized_checkpoint().getEpoch())
            <= 0
        && validator.getActivation_epoch().equals(SpecConfig.FAR_FUTURE_EPOCH);
  }

  @SuppressWarnings("DoNotReturnNullOptionals")
  public Optional<Integer> getValidatorIndex(BeaconState state, BLSPublicKey publicKey) {
    return BeaconStateCache.getTransitionCaches(state)
        .getValidatorIndexCache()
        .getValidatorIndex(state, publicKey);
  }

  /**
   * Decrease validator balance by ``delta`` with underflow protection.
   *
   * @param state
   * @param index
   * @param delta
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#decrease_balance</a>
   */
  public void decreaseBalance(MutableBeaconState state, int index, UInt64 delta) {
    state
        .getBalances()
        .setElement(index, state.getBalances().getElement(index).minusMinZero(delta));
  }

  /**
   * Increase validator balance by ``delta``.
   *
   * @param state
   * @param index
   * @param delta
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#increase_balance</a>
   */
  public void increaseBalance(MutableBeaconState state, int index, UInt64 delta) {
    state.getBalances().setElement(index, state.getBalances().getElement(index).plus(delta));
  }

  /**
   * Determines if a validator has a balance that can be slashed
   *
   * @param validator
   * @param epoch
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_slashable_validator<a/>
   */
  public boolean isSlashableValidator(Validator validator, UInt64 epoch) {
    return !validator.isSlashed()
        && (validator.getActivation_epoch().compareTo(epoch) <= 0
            && epoch.compareTo(validator.getWithdrawable_epoch()) < 0);
  }
}
