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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.ValidatorConstants;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class ValidatorsUtil {

  private final SpecConfig specConfig;
  private final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  public ValidatorsUtil(
      final SpecConfig specConfig,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
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
        && validator.getActivation_epoch().equals(SpecConfig.FAR_FUTURE_EPOCH);
  }

  public Optional<Integer> getValidatorIndex(BeaconState state, BLSPublicKey publicKey) {
    return BeaconStateCache.getTransitionCaches(state)
        .getValidatorIndexCache()
        .getValidatorIndex(state, publicKey);
  }

  /**
   * Return the committee assignment in the ``epoch`` for ``validator_index``. ``assignment``
   * returned is a tuple of the following form: ``assignment[0]`` is the list of validators in the
   * committee ``assignment[1]`` is the index to which the committee is assigned ``assignment[2]``
   * is the slot at which the committee is assigned Return None if no assignment.
   *
   * @param state the BeaconState.
   * @param epoch either on or between previous or current epoch.
   * @param validator_index the validator that is calling this function.
   * @return Optional.of(CommitteeAssignment).
   */
  public Optional<CommitteeAssignment> getCommitteeAssignment(
      BeaconState state, UInt64 epoch, int validator_index) {
    return getCommitteeAssignment(
        state, epoch, validator_index, beaconStateAccessors.getCommitteeCountPerSlot(state, epoch));
  }

  /**
   * Return the committee assignment in the ``epoch`` for ``validator_index``. ``assignment``
   * returned is a tuple of the following form: ``assignment[0]`` is the list of validators in the
   * committee ``assignment[1]`` is the index to which the committee is assigned ``assignment[2]``
   * is the slot at which the committee is assigned Return None if no assignment.
   *
   * @param state the BeaconState.
   * @param epoch either on or between previous or current epoch.
   * @param validator_index the validator that is calling this function.
   * @param committeeCountPerSlot the number of committees for the target epoch
   * @return Optional.of(CommitteeAssignment).
   */
  public Optional<CommitteeAssignment> getCommitteeAssignment(
      BeaconState state, UInt64 epoch, int validator_index, final UInt64 committeeCountPerSlot) {
    UInt64 next_epoch = beaconStateAccessors.getCurrentEpoch(state).plus(UInt64.ONE);
    checkArgument(
        epoch.compareTo(next_epoch) <= 0, "get_committee_assignment: Epoch number too high");

    UInt64 start_slot = miscHelpers.computeStartSlotAtEpoch(epoch);
    for (UInt64 slot = start_slot;
        slot.isLessThan(start_slot.plus(specConfig.getSlotsPerEpoch()));
        slot = slot.plus(UInt64.ONE)) {

      for (UInt64 index = UInt64.ZERO;
          index.compareTo(committeeCountPerSlot) < 0;
          index = index.plus(UInt64.ONE)) {
        final IntList committee = beaconStateAccessors.getBeaconCommittee(state, slot, index);
        if (committee.contains(validator_index)) {
          return Optional.of(new CommitteeAssignment(committee, index, slot));
        }
      }
    }
    return Optional.empty();
  }

  public boolean isAggregator(final BLSSignature slot_signature, final int modulo) {
    return bytesToUInt64(Hash.sha256(slot_signature.toSSZBytes()).slice(0, 8)).mod(modulo).isZero();
  }

  public int getAggregatorModulo(final int committeeSize) {
    return Math.max(1, committeeSize / ValidatorConstants.TARGET_AGGREGATORS_PER_COMMITTEE);
  }
}
