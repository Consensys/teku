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

package tech.pegasys.artemis.validator.client;

import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_crosslink_committees_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_epoch_start_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;

public class ValidatorClient {

  public ValidatorClient() {}

  /**
   * Return the committee assignment in the ``epoch`` for ``validator_index`` and
   * ``registry_change``. ``assignment`` returned is a tuple of the following form: *
   * ``assignment[0]`` is the list of validators in the committee * ``assignment[1]`` is the shard
   * to which the committee is assigned * ``assignment[2]`` is the slot at which the committee is
   * assigned * ``assignment[3]`` is a bool signalling if the validator is expected to propose a
   * beacon block at the assigned slot.
   *
   * @param state the BeaconState.
   * @param epoch either on or between previous or current epoch.
   * @param validator_index the validator that is calling this function.
   * @param registry_change whether there has been a validator registry change.
   * @return Optional.of(CommitteeAssignmentTuple) or Optional.empty.
   */
  public Optional<CommitteeAssignmentTuple> get_committee_assignment(
      BeaconState state, UnsignedLong epoch, int validator_index, boolean registry_change) {
    UnsignedLong previous_epoch = get_previous_epoch(state);
    UnsignedLong next_epoch = get_current_epoch(state);
    assert previous_epoch.compareTo(epoch) <= 0 && epoch.compareTo(next_epoch) <= 0;

    int epoch_start_slot = get_epoch_start_slot(epoch).intValue();

    for (int slot = epoch_start_slot; slot < epoch_start_slot + SLOTS_PER_EPOCH; slot++) {

      ArrayList<CrosslinkCommittee> crosslink_committees =
          get_crosslink_committees_at_slot(state, UnsignedLong.valueOf(slot), registry_change);
      ArrayList<CrosslinkCommittee> selected_committees = new ArrayList<>();

      for (CrosslinkCommittee committee : crosslink_committees) {
        if (committee.getCommittee().contains(validator_index)) {
          selected_committees.add(committee);
        }
      }

      if (selected_committees.size() > 0) {
        List<Integer> validators = selected_committees.get(0).getCommittee();
        int shard = selected_committees.get(0).getShard().intValue();
        List<Integer> first_committee_at_slot =
            crosslink_committees.get(0).getCommittee(); // List[ValidatorIndex]
        boolean is_proposer =
            first_committee_at_slot.get(slot % first_committee_at_slot.size()) == validator_index;

        return Optional.of(new CommitteeAssignmentTuple(validators, shard, slot, is_proposer));
      }
    }
    return Optional.empty();
  }
}
