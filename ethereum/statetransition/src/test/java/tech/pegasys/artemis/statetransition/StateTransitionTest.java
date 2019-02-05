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

import static java.lang.Math.toIntExact;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.crypto.Hash;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.ShardCommittee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.statetransition.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.util.SlotProcessorUtil;

@ExtendWith(BouncyCastleExtension.class)
class StateTransitionTest {

  private BeaconState newState() {
    // Initialize state
    BeaconState state = new BeaconState();

    // Add validator records
    ArrayList<Validator> validators = new ArrayList<Validator>();
    validators.add(
        new Validator(
            Bytes48.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(0)));
    validators.add(
        new Validator(
            Bytes48.leftPad(Bytes.of(100)),
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(0)));
    validators.add(
        new Validator(
            Bytes48.leftPad(Bytes.of(200)),
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(0)));
    validators.add(
        new Validator(
            Bytes48.leftPad(Bytes.of(0)),
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(0)));
    validators.add(
        new Validator(
            Bytes48.leftPad(Bytes.of(0)),
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(0)));
    state.setValidator_registry(validators);

    state.setLatest_block_roots(
        new ArrayList<Bytes32>(
            Collections.nCopies(Constants.LATEST_BLOCK_ROOTS_LENGTH, Constants.ZERO_HASH)));

    return state;
  }

  @Test
  void testUpdateLatestRandaoMixes() {
    BeaconState state = newState();
    state.setLatest_randao_mixes(
        new ArrayList<Bytes32>(
            Collections.nCopies(Constants.LATEST_RANDAO_MIXES_LENGTH, Bytes32.ZERO)));
    state.setSlot(12);
    ArrayList<Bytes32> latestRandaoMixes = state.getLatest_randao_mixes();
    latestRandaoMixes.set(11, Hash.keccak256(Bytes32.fromHexString("0x01")));
    latestRandaoMixes.set(12, Hash.keccak256(Bytes32.ZERO));

    Bytes32 prevSlotRandaoMix = latestRandaoMixes.get(11);
    Bytes32 currSlotRandaoMix = latestRandaoMixes.get(12);
    assertThat(prevSlotRandaoMix).isNotEqualTo(currSlotRandaoMix);

    SlotProcessorUtil.updateLatestRandaoMixes(state);
    prevSlotRandaoMix = latestRandaoMixes.get(11);
    currSlotRandaoMix = latestRandaoMixes.get(12);
    assertThat(prevSlotRandaoMix).isEqualTo(currSlotRandaoMix);
  }

  @Test
  void testUpdateRecentBlockHashes() throws Exception {

    BeaconState state = newState();
    state.setSlot(8192);
    ArrayList<ArrayList<ShardCommittee>> shard_committees_at_slots =
        new ArrayList<ArrayList<ShardCommittee>>();
    for (int i = 0; i < 1000; i++) {
      ArrayList<ShardCommittee> shard_commitees = new ArrayList<ShardCommittee>();
      for (int j = 0; j < 64; j++) {
        int total_validator_count = (int) Math.round(Math.random() * 64);

        ArrayList<Integer> commitee = new ArrayList<Integer>();
        for (int k = 0; k < total_validator_count; k++) {
          commitee.add(Integer.valueOf((int) Math.round(Math.random() * 64)));
        }
        shard_commitees.add(
            new ShardCommittee(
                UnsignedLong.valueOf(Math.round(Math.random() * 5000)),
                commitee,
                UnsignedLong.valueOf((long) total_validator_count)));
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
  }
}
