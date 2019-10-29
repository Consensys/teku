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

package org.ethereum.beacon.consensus.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.SlotNumber;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * A part of spec describing Honest Validator behaviour.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/validator/0_beacon-chain-validator.md">Honest
 *     Validator</a> in the spec.
 */
public interface HonestValidator extends HelperFunction {

  /*
   def get_eth1_vote(state: BeaconState, previous_eth1_distance: uint64) -> Eth1Data:
     new_eth1_data = [get_eth1_data(distance) for distance in range(ETH1_FOLLOW_DISTANCE, 2 * ETH1_FOLLOW_DISTANCE)]
     all_eth1_data = [get_eth1_data(distance) for distance in range(ETH1_FOLLOW_DISTANCE, previous_eth1_distance)]

     valid_votes = []
     for slot, vote in enumerate(state.eth1_data_votes):
         period_tail = slot % SLOTS_PER_ETH1_VOTING_PERIOD >= integer_squareroot(SLOTS_PER_ETH1_VOTING_PERIOD)
         if vote in new_eth1_data or (period_tail and vote in all_eth1_data):
             valid_votes.append(vote)

     return max(
         valid_votes,
         key=lambda v: (valid_votes.count(v), -all_eth1_data.index(v)),  # Tiebreak by smallest distance
         default=get_eth1_data(ETH1_FOLLOW_DISTANCE),
     )
  */
  default Eth1Data get_eth1_vote(
      BeaconState state, UInt64 previous_eth1_distance, Function<Long, Eth1Data> get_eth1_data) {
    List<Eth1Data> new_eth1_data =
        LongStream.range(
                getConstants().getEth1FollowDistance(), getConstants().getEth1FollowDistance() * 2)
            .mapToObj(get_eth1_data::apply)
            .collect(Collectors.toList());

    List<Eth1Data> all_eth1_data =
        LongStream.range(getConstants().getEth1FollowDistance(), previous_eth1_distance.getValue())
            .mapToObj(get_eth1_data::apply)
            .collect(Collectors.toList());

    List<Eth1Data> valid_votes = new ArrayList<>();
    for (int slotNumber = 0; slotNumber < state.getEth1DataVotes().size(); slotNumber++) {
      SlotNumber slot = SlotNumber.of(slotNumber);
      Eth1Data vote = state.getEth1DataVotes().get(slotNumber);
      boolean period_tail =
          slot.modulo(getConstants().getSlotsPerEth1VotingPeriod())
                  .compareTo(integer_squareroot(getConstants().getSlotsPerEth1VotingPeriod()))
              >= 0;
      if (new_eth1_data.contains(vote) || (period_tail && all_eth1_data.contains(vote))) {
        valid_votes.add(vote);
      }
    }

    return valid_votes.stream()
        .max(
            (v1, v2) -> {
              long c1 = valid_votes.stream().filter(v1::equals).count();
              long c2 = valid_votes.stream().filter(v2::equals).count();

              if (c1 == c2) {
                return -Integer.compare(all_eth1_data.indexOf(v1), all_eth1_data.indexOf(v2));
              } else {
                return Long.compare(c1, c2);
              }
            })
        .orElse(get_eth1_data.apply(getConstants().getEth1FollowDistance()));
  }
}
