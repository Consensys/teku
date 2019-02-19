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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.artemis.datastructures.Constants.ENTRY_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.INITIATED_EXIT;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.SEED_LOOKAHEAD;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposits;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.bytes3ToInt;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.exit_validator;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.generate_seed;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.get_active_index_root;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.get_initial_beacon_state;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.is_power_of_two;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.shuffle;
import static tech.pegasys.artemis.statetransition.util.BeaconStateUtil.split;

import com.google.common.primitives.UnsignedLong;
import com.google.gson.Gson;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.crypto.Hash;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.statetransition.util.BeaconStateUtil;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateTest {

  private BeaconState newState() {

    try {
      // Initialize state
      BeaconState state =
          get_initial_beacon_state(
              randomDeposits(5), UnsignedLong.ZERO, new Eth1Data(Bytes32.ZERO, Bytes32.ZERO));

      return state;
    } catch (Exception e) {
      fail("get_initial_beacon_state() failed");
      return null;
    }
  }

  @Test
  void processDepositValidatorPubkeysDoesNotContainPubkeyAndMinEmptyValidatorIndexIsNegative() {
    // TODO: update for 0.1 spec
    /*BeaconState state = newState();
    assertThat(
            state.process_deposit(
                state,
                Bytes48.leftPad(Bytes.of(20)),
                100,
                EMPTY_SIGNATURE,
                Bytes32.ZERO,
                Bytes32.ZERO,
                Bytes32.ZERO))
        .isEqualTo(5);*/
  }

  @Test
  void processDepositValidatorPubkeysDoesNotContainPubkey() {
    BeaconState state = newState();
    // update for 0.1
    /*
    assertThat(
            state.process_deposit(
                state,
                Bytes48.leftPad(Bytes.of(20)),
                100,
                EMPTY_SIGNATURE,
                Bytes32.ZERO,
                Bytes32.ZERO,
                Bytes32.ZERO))
        .isEqualTo(5);
        */
  }

  @Test
  void processDepositValidatorPubkeysContainsPubkey() {
    // TODO: test broken after v0.01 update
    //    BeaconState state = newState();
    //
    //    UnsignedLong oldBalance = state.getValidator_balances().get(2);
    //    Bytes48 pubkey = Bytes48.leftPad(Bytes.of(200));
    //    BeaconStateUtil.process_deposit(
    //        state, pubkey, UnsignedLong.valueOf(100), EMPTY_SIGNATURE, Bytes32.ZERO);
    //
    //    assertThat(state.getValidator_balances().get(2))
    //        .isEqualTo(oldBalance.plus(UnsignedLong.valueOf(100)));
  }

  @Test
  void getAttestationParticipantsSizesNotEqual() {
    AttestationData attestationData =
        new AttestationData(
            UnsignedLong.ZERO,
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            Bytes32.ZERO);
    byte[] participation_bitfield = Bytes32.ZERO.toArrayUnsafe();

    /* todo: fix this test
    assertThrows(
        AssertionError.class,
        () ->
            BeaconState.get_attestation_participants(
                newState(), attestationData, participation_bitfield));
    */
  }

  @Test
  void getAttestationParticipantsReturnsEmptyArrayList() {
    /* todo: fix this test
    AttestationData attestationData =
        new AttestationData(
            0,
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            Bytes32.ZERO);
    byte[] participation_bitfield = new byte[] {1, 1, 1, 1};

    ArrayList<Integer> actual =
        BeaconState.get_attestation_participants(
            newState(), attestationData, participation_bitfield);
    ArrayList<ShardCommittee> expected = new ArrayList<>();

    assertThat(actual).isEqualTo(expected);
    */
  }

  @Test
  void getAttestationParticipantsSuccessful() {
    /* todo Update test for new crosslink committee structure
    BeaconState state = newState();
    ArrayList<ShardCommittee> shard_committee = state.getShard_committees_at_slots().get(64);
    shard_committee.add(
        new ShardCommittee(
            UnsignedLong.ZERO, new ArrayList<>(Collections.nCopies(1, 0)), UnsignedLong.ZERO));
    state.setShard_committees_at_slot(64, shard_committee);

    AttestationData attestationData =
        new AttestationData(
            0,
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            Bytes32.ZERO);
    byte[] participation_bitfield = new byte[] {127, 1, 1};

    ArrayList<ShardCommittee> actual =
        BeaconState.get_attestation_participants(state, attestationData, participation_bitfield);

    assertThat(actual.get(1).getShard()).isEqualTo(UnsignedLong.ZERO);
    assertThat(actual.get(1).getCommittee()).isEqualTo(new ArrayList<>(Collections.nCopies(1, 0)));
    assertThat(actual.get(1).getTotal_validator_count()).isEqualTo(UnsignedLong.ZERO);
    */
  }

  @Test
  void activateValidator() {
    BeaconState state = newState();
    int validator_index = 0;
    UnsignedLong activation_epoch;

    BeaconStateUtil.activate_validator(
        state, state.getValidator_registry().get(validator_index), true);
    activation_epoch = state.getValidator_registry().get(validator_index).getActivation_epoch();
    assertThat(activation_epoch).isEqualTo(UnsignedLong.valueOf(GENESIS_EPOCH));

    BeaconStateUtil.activate_validator(
        state, state.getValidator_registry().get(validator_index), false);
    activation_epoch = state.getValidator_registry().get(validator_index).getActivation_epoch();
    assertThat(activation_epoch)
        .isEqualTo(UnsignedLong.valueOf(GENESIS_EPOCH + 1 + ENTRY_EXIT_DELAY));
  }

  @Test
  void initiateValidatorExitNotActive() {
    BeaconState state = newState();
    int validator_index = 0;

    BeaconStateUtil.initiate_validator_exit(state, validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus_flags())
        .isEqualTo(UnsignedLong.valueOf(INITIATED_EXIT));
  }

  @Test
  void initiateValidatorExit() {
    BeaconState state = newState();
    int validator_index = 2;

    BeaconStateUtil.initiate_validator_exit(state, validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus_flags())
        .isEqualTo(UnsignedLong.valueOf(INITIATED_EXIT));
  }

  @Test
  void exitValidator() {
    /* todo: fixup test for 0.1 spec
    BeaconState state = newState();
    int validator_index = 4;

    exit_validator(state, validator_index);
    Validator validator = state.getValidator_registry().get(validator_index);
    UnsignedLong testEpoch =
        BeaconStateUtil.get_entry_exit_effect_epoch(
            UnsignedLong.valueOf(BeaconStateUtil.get_current_epoch(state)));
    assertThat(validator.getExit_epoch()).isEqualTo(testEpoch);
    */
  }

  @Test
  void exitValidatorPrevStatusExitedWithoutPenaltyNewStatusExitedWithPenalty() {
    BeaconState state = newState();
    int validator_index = 3;

    UnsignedLong before_balance = state.getValidator_balances().get(validator_index);
    //    Hash before_tip = state.validator_registry_delta_chain_tip;

    exit_validator(state, validator_index);

    // TODO: update for 0.1
    // assertThat(state.getValidator_balances().get(validator_index)).isLessThan(before_balance);
    // TODO: Uncomment this when tree_root_hash is working.
    //    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  void exitValidatorPrevStatusDidNotExitNewStatusExitedWithPenalty() {
    BeaconState state = newState();
    // TODO: update for 0.1
    /*
    int validator_index = 2;

    double before_balance = state.getValidator_balances().get(validator_index);
    //    Hash before_tip = state.validator_registry_delta_chain_tip;

    exit_validator(state, validator_index);

    assertThat(state.getValidator_balances().get(validator_index)).isLessThan(before_balance);
    // TODO: Uncomment this when tree_root_hash is working.
    //    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
    */
  }

  @Test
  void exitValidatorPrevStatusDidNotExitNewStatusExitedWithoutPenalty() {
    BeaconState state = newState();
    int validator_index = 0;

    // TODO: update for 0.1
    /*long before_exit_count = state.getValidator_registry_exit_count();
    int before_persistent_committees_size =
        state.getPersistent_committees().get(validator_index).size();
    //    Hash before_tip = state.validator_registry_delta_chain_tip;

    initiate_validator_exit(state, validator_index);
    exit_validator(state, validator_index);

    assertThat(state.getPersistent_committees().get(validator_index).size())
        .isEqualTo(before_persistent_committees_size - 1);
    // TODO: Uncomment this when tree_root_hash is working.
    //    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
    */
  }

  @Test
  void deepCopyBeaconState() {
    BeaconState state = newState();
    BeaconState deepCopy = BeaconState.deepCopy(state);

    // Test if deepCopy has the same values as the original state
    Gson gson = new Gson();
    String stateJson = gson.toJson(state);
    String deepCopyJson = gson.toJson(deepCopy);
    assertThat(stateJson).isEqualTo(deepCopyJson);

    // Test slot
    state.incrementSlot();
    assertThat(deepCopy.getSlot()).isNotEqualTo(state.getSlot());

    // Test fork
    state.setFork(new Fork(UnsignedLong.valueOf(1), UnsignedLong.ONE, UnsignedLong.ONE));
    assertThat(deepCopy.getFork().getPrevious_version())
        .isNotEqualTo(state.getFork().getPrevious_version());

    // Test validator registry
    ArrayList<Validator> new_records =
        new ArrayList<>(
            Collections.nCopies(
                12,
                new Validator(
                    Bytes48.ZERO,
                    Bytes32.ZERO,
                    UnsignedLong.ZERO,
                    UnsignedLong.valueOf(GENESIS_EPOCH),
                    UnsignedLong.ZERO,
                    UnsignedLong.ZERO,
                    UnsignedLong.ZERO)));
    deepCopy.setValidator_registry(new_records);
    // TODO: DeepCopy broken after v0.01 update
    //    assertThat(deepCopy.getValidator_registry().get(0).getPubkey())
    //        .isNotEqualTo(state.getValidator_registry().get(0).getPubkey());
  }

  private Bytes32 hashSrc() {
    Bytes bytes = Bytes.wrap(new byte[] {(byte) 1, (byte) 256, (byte) 65656});
    Security.addProvider(new BouncyCastleProvider());
    return Hash.keccak256(bytes);
  }

  @Test
  void failsWhenInvalidArgumentsBytes3ToInt() {
    assertThrows(
        IllegalArgumentException.class,
        () -> bytes3ToInt(Bytes.wrap(new byte[] {(byte) 0, (byte) 0, (byte) 0}), -1));
  }

  @Test
  void convertBytes3ToInt() {
    // Smoke Tests
    // Test that MSB [00000001][00000000][11110000] LSB == 65656
    assertThat(bytes3ToInt(Bytes.wrap(new byte[] {(byte) 1, (byte) 0, (byte) 120}), 0))
        .isEqualTo(65656);

    // Boundary Tests
    // Test that MSB [00000000][00000000][00000000] LSB == 0
    assertThat(bytes3ToInt(Bytes.wrap(new byte[] {(byte) 0, (byte) 0, (byte) 0}), 0)).isEqualTo(0);
    // Test that MSB [00000000][00000000][11111111] LSB == 255
    assertThat(bytes3ToInt(Bytes.wrap(new byte[] {(byte) 0, (byte) 0, (byte) 255}), 0))
        .isEqualTo(255);
    // Test that MSB [00000000][00000001][00000000] LSB == 256
    assertThat(bytes3ToInt(Bytes.wrap(new byte[] {(byte) 0, (byte) 1, (byte) 0}), 0))
        .isEqualTo(256);
    // Test that MSB [00000000][11111111][11111111] LSB == 65535
    assertThat(bytes3ToInt(Bytes.wrap(new byte[] {(byte) 0, (byte) 255, (byte) 255}), 0))
        .isEqualTo(65535);
    // Test that MSB [00000001][00000000][00000000] LSB == 65536
    assertThat(bytes3ToInt(Bytes.wrap(new byte[] {(byte) 1, (byte) 0, (byte) 0}), 0))
        .isEqualTo(65536);
    // Test that MSB [11111111][11111111][11111111] LSB == 16777215
    assertThat(bytes3ToInt(Bytes.wrap(new byte[] {(byte) 255, (byte) 255, (byte) 255}), 0))
        .isEqualTo(16777215);
  }

  @Test
  void testShuffle() {
    List<Integer> input = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    ArrayList<Integer> sample = new ArrayList<>(input);

    try {
      List<Integer> actual = shuffle(sample, hashSrc());
      List<Integer> expected_input = Arrays.asList(4, 7, 2, 1, 5, 10, 3, 6, 8, 9);
      ArrayList<Integer> expected = new ArrayList<>(expected_input);
      assertThat(actual).isEqualTo(expected);
    } catch (IllegalStateException e) {
      fail(e.toString());
    }
  }

  @Test
  void failsWhenInvalidArgumentTestSplit() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);
    ArrayList<Integer> sample = new ArrayList<>(input);

    assertThrows(IllegalArgumentException.class, () -> split(sample, -1));
  }

  @Test
  void splitReturnsOneSmallerSizedSplit() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);
    ArrayList<Integer> sample = new ArrayList<>(input);

    List<List<Integer>> actual = split(sample, 3);

    ArrayList<ArrayList<Integer>> expected = new ArrayList<>();
    List<Integer> one = Arrays.asList(0, 1);
    expected.add(new ArrayList<>(one));
    List<Integer> two = Arrays.asList(2, 3, 4);
    expected.add(new ArrayList<>(two));
    List<Integer> three = Arrays.asList(5, 6, 7);
    expected.add(new ArrayList<>(three));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void splitReturnsTwoSmallerSizedSplits() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6);
    ArrayList<Integer> sample = new ArrayList<>(input);

    List<List<Integer>> actual = split(sample, 3);

    ArrayList<ArrayList<Integer>> expected = new ArrayList<>();
    List<Integer> one = Arrays.asList(0, 1);
    expected.add(new ArrayList<>(one));
    List<Integer> two = Arrays.asList(2, 3);
    expected.add(new ArrayList<>(two));
    List<Integer> three = Arrays.asList(4, 5, 6);
    expected.add(new ArrayList<>(three));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void splitReturnsEquallySizedSplits() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);
    ArrayList<Integer> sample = new ArrayList<>(input);

    List<List<Integer>> actual = split(sample, 3);

    ArrayList<ArrayList<Integer>> expected = new ArrayList<>();
    List<Integer> one = Arrays.asList(0, 1, 2);
    expected.add(new ArrayList<>(one));
    List<Integer> two = Arrays.asList(3, 4, 5);
    expected.add(new ArrayList<>(two));
    List<Integer> three = Arrays.asList(6, 7, 8);
    expected.add(new ArrayList<>(three));

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void isPowerOfTwo() {
    assertThat(is_power_of_two(UnsignedLong.ZERO)).isEqualTo(false);
    assertThat(is_power_of_two(UnsignedLong.valueOf(42L))).isEqualTo(false);
    assertThat(is_power_of_two(UnsignedLong.valueOf(Long.MAX_VALUE))).isEqualTo(false);
    assertThat(is_power_of_two(UnsignedLong.ONE)).isEqualTo(true);
    assertThat(is_power_of_two(UnsignedLong.ONE.plus(UnsignedLong.ONE))).isEqualTo(true);
    assertThat(is_power_of_two(UnsignedLong.valueOf(65536L))).isEqualTo(true);
    assertThat(is_power_of_two(UnsignedLong.valueOf(4611686018427387904L))).isEqualTo(true);
  }

  @Test
  void getRandaoMixThrowsExceptions() {
    BeaconState state = newState();
    state.setSlot(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH * EPOCH_LENGTH));
    assertThat(get_current_epoch(state).compareTo(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
        .isEqualTo(0);
    // Test `assert get_current_epoch(state) - LATEST_RANDAO_MIXES_LENGTH < epoch`
    assertThrows(RuntimeException.class, () -> get_randao_mix(state, UnsignedLong.ZERO));
    // Test `assert epoch <= get_current_epoch(state)`
    assertThrows(
        RuntimeException.class,
        () ->
            get_randao_mix(
                state, UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH).plus(UnsignedLong.ONE)));
  }

  @Test
  void getRandaoMixReturnsCorrectValue() {
    BeaconState state = newState();
    state.setSlot(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH * EPOCH_LENGTH));
    assertThat(get_current_epoch(state).compareTo(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
        .isEqualTo(0);
    List<Bytes32> latest_randao_mixes = state.getLatest_randao_mixes();
    latest_randao_mixes.set(0, Bytes32.fromHexString("0x42"));
    latest_randao_mixes.set(1, Bytes32.fromHexString("0x029a"));
    latest_randao_mixes.set(LATEST_RANDAO_MIXES_LENGTH - 1, Bytes32.fromHexString("0xdeadbeef"));

    assertThat(get_randao_mix(state, UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
        .isEqualTo(Bytes32.fromHexString("0x42"));
    assertThat(get_randao_mix(state, UnsignedLong.valueOf(1)))
        .isEqualTo(Bytes32.fromHexString("0x029a"));
    assertThat(get_randao_mix(state, UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH - 1)))
        .isEqualTo(Bytes32.fromHexString("0xdeadbeef"));
  }

  @Test
  void generateSeedReturnsCorrectValue() {
    BeaconState state = newState();
    state.setSlot(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH * EPOCH_LENGTH));
    assertThat(get_current_epoch(state).compareTo(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
        .isEqualTo(0);
    List<Bytes32> latest_randao_mixes = state.getLatest_randao_mixes();
    latest_randao_mixes.set(ENTRY_EXIT_DELAY + 1, Bytes32.fromHexString("0x029a"));

    UnsignedLong epoch = UnsignedLong.valueOf(ENTRY_EXIT_DELAY + SEED_LOOKAHEAD + 1);
    Bytes32 randao_mix = get_randao_mix(state, epoch.minus(UnsignedLong.valueOf(SEED_LOOKAHEAD)));
    assertThat(randao_mix).isEqualTo(Bytes32.fromHexString("0x029a"));
    try {
      Security.addProvider(new BouncyCastleProvider());
      assertThat(generate_seed(state, epoch))
          .isEqualTo(
              Hash.keccak256(
                  Bytes.wrap(
                      Bytes32.fromHexString("0x029a"), get_active_index_root(state, epoch))));
    } catch (IllegalStateException e) {
      fail(e.toString());
    }
  }

  @Test
  void testkeccak256Hashof0() {
    Bytes32 randao_mix = Bytes32.ZERO;
    Bytes32 index_root = Bytes32.ZERO;
    Security.addProvider(new BouncyCastleProvider());
    Bytes32 hash = Hash.keccak256(Bytes.wrap(randao_mix, index_root));
  }
}
