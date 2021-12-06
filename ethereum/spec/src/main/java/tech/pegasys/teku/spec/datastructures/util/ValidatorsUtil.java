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

package tech.pegasys.teku.spec.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.util.config.Constants.MAX_SEED_LOOKAHEAD;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import java.util.stream.IntStream;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

@Deprecated
class ValidatorsUtil {

  /**
   * Check if (this) validator is active in the given epoch.
   *
   * @param epoch - The epoch under consideration.
   * @return A boolean indicating if the validator is active.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_active_validator
   *     </a>
   */
  @Deprecated
  private static boolean is_active_validator(Validator validator, UInt64 epoch) {
    return validator.getActivation_epoch().compareTo(epoch) <= 0
        && epoch.compareTo(validator.getExit_epoch()) < 0;
  }

  /**
   * Get active validator indices at ``epoch``.
   *
   * @param state - Current BeaconState
   * @param epoch - The epoch under consideration.
   * @return A list of indices representing the active validators for the given epoch.
   */
  @Deprecated
  static IntList get_active_validator_indices(BeaconState state, UInt64 epoch) {
    final UInt64 stateEpoch = BeaconStateUtil.get_current_epoch(state);
    final UInt64 maxLookaheadEpoch = getMaxLookaheadEpoch(stateEpoch);
    checkArgument(
        epoch.isLessThanOrEqualTo(maxLookaheadEpoch),
        "Cannot get active validator indices from an epoch beyond the seed lookahead period. Requested epoch %s from state in epoch %s",
        epoch,
        stateEpoch);
    return BeaconStateCache.getTransitionCaches(state)
        .getActiveValidators()
        .get(
            epoch,
            e -> {
              SszList<Validator> validators = state.getValidators();
              return IntList.of(
                  IntStream.range(0, validators.size())
                      .filter(index -> is_active_validator(validators.get(index), epoch))
                      .toArray());
            });
  }

  private static UInt64 getMaxLookaheadEpoch(final UInt64 stateEpoch) {
    return stateEpoch.plus(MAX_SEED_LOOKAHEAD);
  }

  @Deprecated
  @SuppressWarnings("DoNotReturnNullOptionals")
  static Optional<Integer> getValidatorIndex(BeaconState state, BLSPublicKey publicKey) {
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
  @Deprecated
  static void decrease_balance(MutableBeaconState state, int index, UInt64 delta) {
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
  @Deprecated
  static void increase_balance(MutableBeaconState state, int index, UInt64 delta) {
    state.getBalances().setElement(index, state.getBalances().getElement(index).plus(delta));
  }
}
