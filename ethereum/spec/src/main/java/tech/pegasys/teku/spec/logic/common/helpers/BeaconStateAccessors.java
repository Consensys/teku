/*
 * Copyright 2021 ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uintToBytes;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.type.Bytes4;

public class BeaconStateAccessors {
  protected final SpecConfig config;
  protected final Predicates predicates;
  protected final MiscHelpers miscHelpers;

  public BeaconStateAccessors(
      final SpecConfig config, final Predicates predicates, final MiscHelpers miscHelpers) {
    this.config = config;
    this.predicates = predicates;
    this.miscHelpers = miscHelpers;
  }

  public UInt64 getCurrentEpoch(BeaconState state) {
    return miscHelpers.computeEpochAtSlot(state.getSlot());
  }

  public UInt64 getPreviousEpoch(BeaconState state) {
    UInt64 currentEpoch = getCurrentEpoch(state);
    return currentEpoch.equals(GENESIS_EPOCH) ? GENESIS_EPOCH : currentEpoch.minus(UInt64.ONE);
  }

  public UInt64 getValidatorChurnLimit(BeaconState state) {
    final int activeValidatorCount =
        getActiveValidatorIndices(state, getCurrentEpoch(state)).size();
    return getValidatorChurnLimit(activeValidatorCount);
  }

  public UInt64 getValidatorChurnLimit(final int activeValidatorCount) {
    return UInt64.valueOf(config.getMinPerEpochChurnLimit())
        .max(UInt64.valueOf(activeValidatorCount / config.getChurnLimitQuotient()));
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
                  BLSPublicKey pubKey = state.getValidators().get(i.intValue()).getPublicKey();

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
    final UInt64 stateEpoch = getCurrentEpoch(state);
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
              return IntStream.range(0, validators.size())
                  .filter(index -> predicates.isActiveValidator(validators.get(index), epoch))
                  .boxed()
                  .collect(Collectors.toList());
            });
  }

  public UInt64 getMaxLookaheadEpoch(final BeaconState state) {
    return getMaxLookaheadEpoch(getCurrentEpoch(state));
  }

  private UInt64 getMaxLookaheadEpoch(final UInt64 stateEpoch) {
    return stateEpoch.plus(config.getMaxSeedLookahead());
  }

  public UInt64 getTotalBalance(BeaconState state, Collection<Integer> indices) {
    UInt64 sum = UInt64.ZERO;
    SszList<Validator> validator_registry = state.getValidators();
    for (Integer index : indices) {
      sum = sum.plus(validator_registry.get(index).getEffective_balance());
    }
    return sum.max(config.getEffectiveBalanceIncrement());
  }

  public UInt64 getTotalActiveBalance(BeaconState state) {
    return BeaconStateCache.getTransitionCaches(state)
        .getTotalActiveBalance()
        .get(
            getCurrentEpoch(state),
            epoch -> getTotalBalance(state, getActiveValidatorIndices(state, epoch)));
  }

  public Bytes32 getSeed(BeaconState state, UInt64 epoch, Bytes4 domain_type)
      throws IllegalArgumentException {
    UInt64 randaoIndex =
        epoch.plus(config.getEpochsPerHistoricalVector() - config.getMinSeedLookahead() - 1);
    Bytes32 mix = getRandaoMix(state, randaoIndex);
    Bytes epochBytes = uintToBytes(epoch.longValue(), 8);
    return Hash.sha2_256(Bytes.concatenate(domain_type.getWrappedBytes(), epochBytes, mix));
  }

  public Bytes32 getRandaoMix(BeaconState state, UInt64 epoch) {
    int index = epoch.mod(config.getEpochsPerHistoricalVector()).intValue();
    return state.getRandao_mixes().getElement(index);
  }

  public int getBeaconProposerIndex(BeaconState state) {
    return getBeaconProposerIndex(state, state.getSlot());
  }

  public int getBeaconProposerIndex(BeaconState state, UInt64 requestedSlot) {
    validateStateCanCalculateProposerIndexAtSlot(state, requestedSlot);
    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconProposerIndex()
        .get(
            requestedSlot,
            slot -> {
              UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
              Bytes32 seed =
                  Hash.sha2_256(
                      Bytes.concatenate(
                          getSeed(state, epoch, config.getDomainBeaconProposer()),
                          uintToBytes(slot.longValue(), 8)));
              List<Integer> indices = getActiveValidatorIndices(state, epoch);
              return miscHelpers.computeProposerIndex(state, indices, seed);
            });
  }

  private void validateStateCanCalculateProposerIndexAtSlot(
      final BeaconState state, final UInt64 requestedSlot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(requestedSlot);
    final UInt64 stateEpoch = getCurrentEpoch(state);
    checkArgument(
        epoch.equals(stateEpoch),
        "Cannot calculate proposer index for a slot outside the current epoch. Requested slot %s (in epoch %s), state slot %s (in epoch %s)",
        requestedSlot,
        epoch,
        state.getSlot(),
        stateEpoch);
  }
}
