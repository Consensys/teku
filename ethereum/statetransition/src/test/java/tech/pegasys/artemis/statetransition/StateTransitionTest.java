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

package tech.pegasys.artemis.statetransition;

import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposits;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;

@ExtendWith(BouncyCastleExtension.class)
class StateTransitionTest {

  private BeaconState newState() {
    try {
      // Initialize state
      BeaconStateWithCache state = new BeaconStateWithCache();
      BeaconStateUtil.get_initial_beacon_state(
          state, randomDeposits(5), UnsignedLong.ZERO, new Eth1Data(Bytes32.ZERO, Bytes32.ZERO));

      state.setLatest_block_roots(
          new ArrayList<Bytes32>(
              Collections.nCopies(Constants.LATEST_BLOCK_ROOTS_LENGTH, Constants.ZERO_HASH)));

      return state;
    } catch (Exception e) {
      fail("get_initial_beacon_state() failed");
      return null;
    }
  }

  @Test
  void testUpdateRecentBlockHashes() throws Exception {

    /* todo: rework test - shard_committees_at_slots no longer exists
    BeaconState state = newState();
    state.setSlot(8192);

    ArrayList<ArrayList<ShardCommittee>> shard_committees_at_slots =
        new ArrayList<ArrayList<ShardCommittee>>();
    for (int i = 0; i < 1000; i++) {
      ArrayList<ShardCommittee> shard_commitees = new ArrayList<ShardCommittee>();
      for (int j = 0; j < 64; j++) {
        int total_validator_count = (int) Math.round(Math.random() * 64);

        ArrayList<Integer> committee = new ArrayList<Integer>();
        for (int k = 0; k < total_validator_count; k++) {
          committee.add(Integer.valueOf((int) Math.round(Math.random() * 64)));
        }
        shard_commitees.add(
            new ShardCommittee(UnsignedLong.valueOf(Math.round(Math.random() * 5000)), committee));
      }
      shard_committees_at_slots.add(shard_commitees);
    }

    state.setShard_committees_at_slots(shard_committees_at_slots);
    BeaconBlock block = new BeaconBlock();
    block.setState_root(Bytes32.ZERO);
    SlotProcessorUtil.updateRecentBlockHashes(state, block);

    assertThat(state.getLatest_block_roots().get(toIntExact(state.getSlot() - 1)))
        .isEqualTo(block.getState_root());
    ArrayList<Bytes32> batched_block_roots = state.getBatched_block_roots();
    assertThat(batched_block_roots.get(batched_block_roots.size() - 1))
        .isEqualTo(BeaconStateUtil.merkle_root(state.getLatest_block_roots()));
    */
  }
}
