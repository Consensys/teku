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

package tech.pegasys.teku.core;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_committee_count_per_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CommitteeAssignmentUtil {

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
  public static Optional<CommitteeAssignment> get_committee_assignment(
      BeaconState state, UInt64 epoch, int validator_index) {
    return get_committee_assignment(
        state, epoch, validator_index, get_committee_count_per_slot(state, epoch));
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
  public static Optional<CommitteeAssignment> get_committee_assignment(
      BeaconState state, UInt64 epoch, int validator_index, final UInt64 committeeCountPerSlot) {
    UInt64 next_epoch = get_current_epoch(state).plus(UInt64.ONE);
    checkArgument(
        epoch.compareTo(next_epoch) <= 0, "get_committee_assignment: Epoch number too high");

    UInt64 start_slot = compute_start_slot_at_epoch(epoch);
    for (UInt64 slot = start_slot;
        slot.isLessThan(start_slot.plus(SLOTS_PER_EPOCH));
        slot = slot.plus(UInt64.ONE)) {

      for (UInt64 index = UInt64.ZERO;
          index.compareTo(committeeCountPerSlot) < 0;
          index = index.plus(UInt64.ONE)) {
        final List<Integer> committee = get_beacon_committee(state, slot, index);
        if (committee.contains(validator_index)) {
          return Optional.of(new CommitteeAssignment(committee, index, slot));
        }
      }
    }
    return Optional.empty();
  }
}
