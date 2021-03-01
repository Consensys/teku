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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class ValidatorsUtil {

  private final SpecConstants specConstants;
  private final ChainTimingUtil chainTimingUtil;

  public ValidatorsUtil(final SpecConstants specConstants, final ChainTimingUtil chainTimingUtil) {
    this.specConstants = specConstants;
    this.chainTimingUtil = chainTimingUtil;
  }

  /**
   * Check if (this) validator is active in the given epoch.
   *
   * @param epoch - The epoch under consideration.
   * @return A boolean indicating if the validator is active.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_active_validator
   *     </a>
   */
  public boolean isActiveValidator(Validator validator, UInt64 epoch) {
    return validator.getActivation_epoch().compareTo(epoch) <= 0
        && epoch.compareTo(validator.getExit_epoch()) < 0;
  }

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
        && validator.getActivation_epoch().equals(SpecConstants.FAR_FUTURE_EPOCH);
  }

  public Optional<BLSPublicKey> getValidatorPubKey(BeaconState state, UInt64 validatorIndex) {
    if (state.getValidators().size() <= validatorIndex.longValue()
        || validatorIndex.longValue() < 0) {
      return Optional.empty();
    }
    return Optional.of(
        BeaconStateCache.getTransitionCaches(state)
            .getValidatorsPubKeys()
            .get(
                validatorIndex,
                i -> {
                  BLSPublicKey pubKey =
                      BLSPublicKey.fromBytesCompressed(
                          state.getValidators().get(i.intValue()).getPubkey());

                  // eagerly pre-cache pubKey => validatorIndex mapping
                  BeaconStateCache.getTransitionCaches(state)
                      .getValidatorIndexCache()
                      .invalidateWithNewValue(pubKey, i.intValue());
                  return pubKey;
                }));
  }

  /**
   * Get active validator indices at ``epoch``.
   *
   * @param state - Current BeaconState
   * @param epoch - The epoch under consideration.
   * @return A list of indices representing the active validators for the given epoch.
   */
  public List<Integer> getActiveValidatorIndices(BeaconState state, UInt64 epoch) {
    final UInt64 stateEpoch = chainTimingUtil.getCurrentEpoch(state);
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
              SSZList<Validator> validators = state.getValidators();
              return IntStream.range(0, validators.size())
                  .filter(index -> isActiveValidator(validators.get(index), epoch))
                  .boxed()
                  .collect(Collectors.toList());
            });
  }

  public UInt64 getMaxLookaheadEpoch(final BeaconState state) {
    return getMaxLookaheadEpoch(chainTimingUtil.getCurrentEpoch(state));
  }

  private UInt64 getMaxLookaheadEpoch(final UInt64 stateEpoch) {
    return stateEpoch.plus(specConstants.getMaxSeedLookahead());
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
    state.getBalances().set(index, state.getBalances().get(index).minusMinZero(delta));
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
  public boolean isSlashableValidator(Validator validator, UInt64 epoch) {
    return !validator.isSlashed()
        && (validator.getActivation_epoch().compareTo(epoch) <= 0
            && epoch.compareTo(validator.getWithdrawable_epoch()) < 0);
  }
}
