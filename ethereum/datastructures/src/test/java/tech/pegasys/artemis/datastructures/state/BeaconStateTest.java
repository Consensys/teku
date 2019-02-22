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

package tech.pegasys.artemis.datastructures.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.artemis.datastructures.Constants.ENTRY_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.INITIATED_EXIT;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.SEED_LOOKAHEAD;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.bytes3ToInt;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.generate_seed;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_active_index_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_initial_beacon_state;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_power_of_two;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.shuffle;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.split;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposits;

import com.google.common.primitives.UnsignedLong;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateTest {

  private BeaconState newState(int numDeposits) {

    try {
      // Initialize state
      BeaconState state =
          get_initial_beacon_state(
              randomDeposits(numDeposits),
              UnsignedLong.ZERO,
              new Eth1Data(Bytes32.ZERO, Bytes32.ZERO));

      return state;
    } catch (Exception e) {
      fail("get_initial_beacon_state() failed");
      return null;
    }
  }

  @Disabled
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

  @Disabled
  @Test
  void processDepositValidatorPubkeysDoesNotContainPubkey() {
    // BeaconState state = newState();
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

  @Disabled
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

  @Disabled
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
    byte[] aggregation_bitfield = Bytes32.ZERO.toArrayUnsafe();

    /* todo: fix this test
    assertThrows(
        AssertionError.class,
        () ->
            BeaconState.get_attestation_participants(
                newState(), attestationData, aggregation_bitfield));
    */
  }

  @Disabled
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
    byte[] aggregation_bitfield = new byte[] {1, 1, 1, 1};

    ArrayList<Integer> actual =
        BeaconState.get_attestation_participants(
            newState(), attestationData, aggregation_bitfield);
    ArrayList<ShardCommittee> expected = new ArrayList<>();

    assertThat(actual).isEqualTo(expected);
    */
  }

  @Disabled
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
    byte[] aggregation_bitfield = new byte[] {127, 1, 1};

    ArrayList<ShardCommittee> actual =
        BeaconState.get_attestation_participants(state, attestationData, aggregation_bitfield);

    assertThat(actual.get(1).getShard()).isEqualTo(UnsignedLong.ZERO);
    assertThat(actual.get(1).getCommittee()).isEqualTo(new ArrayList<>(Collections.nCopies(1, 0)));
    assertThat(actual.get(1).getTotal_validator_count()).isEqualTo(UnsignedLong.ZERO);
    */
  }

  @Test
  void activateValidator() {
    BeaconState state = newState(5);
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
    BeaconState state = newState(5);
    int validator_index = 0;

    BeaconStateUtil.initiate_validator_exit(state, validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus_flags())
        .isEqualTo(UnsignedLong.valueOf(INITIATED_EXIT));
  }

  @Test
  void initiateValidatorExit() {
    BeaconState state = newState(5);
    int validator_index = 2;

    BeaconStateUtil.initiate_validator_exit(state, validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus_flags())
        .isEqualTo(UnsignedLong.valueOf(INITIATED_EXIT));
  }

  @Test
  void deepCopyBeaconState() {
    BeaconState state = newState(1);
    BeaconState deepCopy = BeaconState.deepCopy(state);

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
    assertThat(deepCopy.getValidator_registry().get(0).getPubkey())
        .isNotEqualTo(state.getValidator_registry().get(0).getPubkey());
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
    BeaconState state = newState(5);
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
    BeaconState state = newState(5);
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
    BeaconState state = newState(5);
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
  void rountripSSZ() {
    BeaconState state = newState(1);
    Bytes sszBeaconBlockBytes = state.toBytes();
    assertEquals(state, BeaconState.fromBytes(sszBeaconBlockBytes));
  }
}
