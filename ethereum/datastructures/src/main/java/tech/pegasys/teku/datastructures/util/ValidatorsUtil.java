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

package tech.pegasys.teku.datastructures.util;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateCache;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.util.config.Constants;

public class ValidatorsUtil {

  /**
   * Check if (this) validator is active in the given epoch.
   *
   * @param epoch - The epoch under consideration.
   * @return A boolean indicating if the validator is active.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_active_validator
   *     </a>
   */
  public static boolean is_active_validator(Validator validator, UInt64 epoch) {
    return validator.getActivation_epoch().compareTo(epoch) <= 0
        && epoch.compareTo(validator.getExit_epoch()) < 0;
  }

  /**
   * Check if validator is eligible to be placed into the activation queue.
   *
   * @param validator the validator
   * @return true if eligible for the activation queue otherwise false
   */
  public static boolean is_eligible_for_activation_queue(Validator validator) {
    return validator.getActivation_eligibility_epoch().equals(Constants.FAR_FUTURE_EPOCH)
        && validator.getEffective_balance().equals(UInt64.valueOf(Constants.MAX_EFFECTIVE_BALANCE));
  }

  /**
   * Check if validator is eligible for activation.
   *
   * @param state the beacon state
   * @param validator the validator
   * @return true if the validator is eligible for activation
   */
  public static boolean is_eligible_for_activation(BeaconState state, Validator validator) {
    return validator
                .getActivation_eligibility_epoch()
                .compareTo(state.getFinalized_checkpoint().getEpoch())
            <= 0
        && validator.getActivation_epoch().equals(Constants.FAR_FUTURE_EPOCH);
  }

  public static Optional<BLSPublicKey> getValidatorPubKey(
      BeaconState state, UInt64 validatorIndex) {
    if (state.getValidators().size() <= validatorIndex.longValue()
        || validatorIndex.longValue() < 0) {
      return Optional.empty();
    }
    Optional<BLSPublicKey> ret =
        Optional.of(
            BeaconStateCache.getTransitionCaches(state)
                .getValidatorsPubKeys()
                .get(
                    validatorIndex,
                    i ->
                        BLSPublicKey.fromBytesCompressed(
                            state.getValidators().get(i.intValue()).getPubkey())));

    // eagerly pre-cache pubKey => validatorIndex mapping
    ret.ifPresent(
        pubKey ->
            BeaconStateCache.getTransitionCaches(state)
                .getValidatorIndex()
                .invalidateWithNewValue(pubKey, validatorIndex.intValue()));
    return ret;
  }

  /**
   * Get active validator indices at ``epoch``.
   *
   * @param state - Current BeaconState
   * @param epoch - The epoch under consideration.
   * @return A list of indices representing the active validators for the given epoch.
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_active_validator_indices</a>
   */
  public static List<Integer> get_active_validator_indices(BeaconState state, UInt64 epoch) {
    return BeaconStateCache.getTransitionCaches(state)
        .getActiveValidators()
        .get(
            epoch,
            e -> {
              SSZList<Validator> validators = state.getValidators();
              return IntStream.range(0, validators.size())
                  .filter(index -> is_active_validator(validators.get(index), epoch))
                  .boxed()
                  .collect(Collectors.toList());
            });
  }

  @SuppressWarnings("DoNotReturnNullOptionals")
  public static Optional<Integer> getValidatorIndex(BeaconState state, BLSPublicKey publicKey) {
    final Integer validatorIndex =
        BeaconStateCache.getTransitionCaches(state)
            .getValidatorIndex()
            .get(
                publicKey,
                key -> {
                  SSZList<Validator> validators = state.getValidators();
                  for (int i = 0; i < validators.size(); i++) {
                    if (getValidatorPubKey(state, UInt64.valueOf(i))
                        .orElseThrow()
                        .equals(publicKey)) {
                      return i;
                    }
                  }
                  return null;
                });
    return Optional.ofNullable(validatorIndex)
        // The cache is shared between all states, so filter out any cached responses for validators
        // that are only added into later states
        .filter(index -> index < state.getValidators().size());
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
  public static void decrease_balance(MutableBeaconState state, int index, UInt64 delta) {
    UInt64 newBalance =
        delta.compareTo(state.getBalances().get(index)) > 0
            ? UInt64.ZERO
            : state.getBalances().get(index).minus(delta);
    state.getBalances().set(index, newBalance);
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
  public static void increase_balance(MutableBeaconState state, int index, UInt64 delta) {
    state.getBalances().set(index, state.getBalances().get(index).plus(delta));
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
  public static boolean is_slashable_validator(Validator validator, UInt64 epoch) {
    return !validator.isSlashed()
        && (validator.getActivation_epoch().compareTo(epoch) <= 0
            && epoch.compareTo(validator.getWithdrawable_epoch()) < 0);
  }
}
