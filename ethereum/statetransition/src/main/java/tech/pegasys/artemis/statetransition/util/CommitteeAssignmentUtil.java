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

package tech.pegasys.artemis.statetransition.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;

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
      BeaconState state, UnsignedLong epoch, int validator_index) {
    UnsignedLong next_epoch = get_current_epoch(state).plus(UnsignedLong.ONE);
    checkArgument(
        epoch.compareTo(next_epoch) <= 0,
        "get_committee_assignment: Epoch number too high - epoch {} - next_epoch {}",
        epoch,
        next_epoch);

    UnsignedLong start_slot = compute_start_slot_at_epoch(epoch);

    for (UnsignedLong slot = start_slot;
        slot.compareTo(start_slot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH))) < 0;
        slot = slot.plus(UnsignedLong.ONE)) {

      final UnsignedLong committeeCountAtSlot = get_committee_count_at_slot(state, slot);
      for (UnsignedLong index = UnsignedLong.ZERO;
          index.compareTo(committeeCountAtSlot) < 0;
          index = index.plus(UnsignedLong.ONE)) {
        final List<Integer> committee = get_beacon_committee(state, slot, index);
        if (committee.contains(validator_index)) {
          return Optional.of(new CommitteeAssignment(committee, index, slot));
        }
      }
    }
    return Optional.empty();
  }
}
