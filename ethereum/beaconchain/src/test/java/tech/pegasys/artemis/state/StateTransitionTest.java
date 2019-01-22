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

package tech.pegasys.artemis.state;

import static java.lang.Math.toIntExact;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.Constants.ACTIVE;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;
import static tech.pegasys.artemis.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.Constants.EXITED_WITHOUT_PENALTY;
import static tech.pegasys.artemis.Constants.EXITED_WITH_PENALTY;
import static tech.pegasys.artemis.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.Constants.PENDING_ACTIVATION;

import com.google.common.primitives.UnsignedLong;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.crypto.Hash;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import tech.pegasys.artemis.datastructures.beaconchainblocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.beaconchainstate.ShardCommittee;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.state.util.SlotProcessorUtil;

public class StateTransitionTest {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  private BeaconState newState() {
    // Initialize state
    BeaconState state = new BeaconState();

    // Add validator records
    ArrayList<ValidatorRecord> validators = new ArrayList<ValidatorRecord>();
    validators.add(
        new ValidatorRecord(
            Bytes48.leftPad(Bytes.of(0)),
            Bytes32.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(PENDING_ACTIVATION),
            UnsignedLong.valueOf(state.getSlot()),
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO));
    validators.add(
        new ValidatorRecord(
            Bytes48.leftPad(Bytes.of(100)),
            Bytes32.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(ACTIVE),
            UnsignedLong.valueOf(state.getSlot()),
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO));
    validators.add(
        new ValidatorRecord(
            Bytes48.leftPad(Bytes.of(200)),
            Bytes32.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(ACTIVE_PENDING_EXIT),
            UnsignedLong.valueOf(state.getSlot()),
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO));
    validators.add(
        new ValidatorRecord(
            Bytes48.leftPad(Bytes.of(0)),
            Bytes32.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(EXITED_WITHOUT_PENALTY),
            UnsignedLong.valueOf(state.getSlot()),
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO));
    validators.add(
        new ValidatorRecord(
            Bytes48.leftPad(Bytes.of(0)),
            Bytes32.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.valueOf(EXITED_WITH_PENALTY),
            UnsignedLong.valueOf(state.getSlot()),
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            UnsignedLong.ZERO));
    state.setValidator_registry(validators);

    return state;
  }

  @Test
  public void testUpdateProposerRandaoLayer() {
    // initialize state and state transition objects
    BeaconState state = newState();
    state.setSlot(4);
    ArrayList<Integer> commitee = new ArrayList<Integer>();
    commitee.add(Integer.valueOf(2));
    commitee.add(Integer.valueOf(4));
    commitee.add(Integer.valueOf(1));
    commitee.add(Integer.valueOf(0));
    commitee.add(Integer.valueOf(3));
    ArrayList<ShardCommittee> newShardCommittees =
        new ArrayList<ShardCommittee>(
            Collections.nCopies(
                2, new ShardCommittee(UnsignedLong.ZERO, commitee, UnsignedLong.ONE)));
    state.setShard_committees_at_slots(
        new ArrayList<ArrayList<ShardCommittee>>(
            Collections.nCopies(2 * EPOCH_LENGTH, newShardCommittees)));

    StateTransition stateTransition = new StateTransition();

    // get proposer's and a random validator's validator record
    // proposer_index is 3 here since we've set slot to 4
    int currSlot = toIntExact(state.getSlot());
    int proposerIndex = BeaconState.get_beacon_proposer_index(state, currSlot);
    assertThat(proposerIndex).isEqualTo(3);

    ArrayList<ValidatorRecord> validatorRegistry = state.getValidator_registry();
    ValidatorRecord proposerRecord = validatorRegistry.get(proposerIndex);
    ValidatorRecord randomRecord = validatorRegistry.get(1);

    // obtain proposer's and a random validator's randao layer value
    // before and after update method is called
    long proposerRandaoLayerBefore = proposerRecord.getRandao_layers().longValue();
    long randomRandaoLayerBefore = randomRecord.getRandao_layers().longValue();
    SlotProcessorUtil.updateProposerRandaoLayer(state);
    long proposerRandaoLayerAfter = proposerRecord.getRandao_layers().longValue();
    long randomRandaoLayerAfter = randomRecord.getRandao_layers().longValue();

    assertThat(proposerRandaoLayerBefore + 1).isEqualTo(proposerRandaoLayerAfter);
    assertThat(randomRandaoLayerBefore).isEqualTo(randomRandaoLayerAfter);
  }

  @Test
  public void testUpdateLatestRandaoMixes() {
    BeaconState state = newState();
    state.setLatest_randao_mixes(
        new ArrayList<Bytes32>(Collections.nCopies(LATEST_RANDAO_MIXES_LENGTH, Bytes32.ZERO)));
    state.setSlot(12);
    ArrayList<Bytes32> latestRandaoMixes = state.getLatest_randao_mixes();
    latestRandaoMixes.set(11, Hash.keccak256(Bytes32.fromHexString("0x01")));
    latestRandaoMixes.set(12, Hash.keccak256(Bytes32.ZERO));
    StateTransition stateTransition = new StateTransition();

    Bytes32 prevSlotRandaoMix = latestRandaoMixes.get(11);
    Bytes32 currSlotRandaoMix = latestRandaoMixes.get(12);
    assertThat(prevSlotRandaoMix).isNotEqualTo(currSlotRandaoMix);

    SlotProcessorUtil.updateLatestRandaoMixes(state);
    prevSlotRandaoMix = latestRandaoMixes.get(11);
    currSlotRandaoMix = latestRandaoMixes.get(12);
    assertThat(prevSlotRandaoMix).isEqualTo(currSlotRandaoMix);
  }

  @Test
  public void testUpdateRecentBlockHashes() throws StateTransitionException {
    BeaconState state = newState();
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

    assertThat(state.getLatest_block_roots().get(UnsignedLong.valueOf(state.getSlot())))
        .isEqualTo(block.getState_root());
    ArrayList<Bytes32> batched_block_roots = state.getBatched_block_roots();
    assertThat(batched_block_roots.get(batched_block_roots.size() - 1))
        .isEqualTo(SlotProcessorUtil.merkle_root(state.getLatest_block_roots()));
  }
}
