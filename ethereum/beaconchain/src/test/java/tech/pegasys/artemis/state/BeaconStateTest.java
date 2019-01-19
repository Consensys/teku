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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.Constants.ACTIVATION;
import static tech.pegasys.artemis.Constants.ACTIVE;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;
import static tech.pegasys.artemis.Constants.EMPTY_SIGNATURE;
import static tech.pegasys.artemis.Constants.EXITED_WITHOUT_PENALTY;
import static tech.pegasys.artemis.Constants.EXITED_WITH_PENALTY;
import static tech.pegasys.artemis.Constants.PENDING_ACTIVATION;
import static tech.pegasys.artemis.state.BeaconState.BeaconStateHelperFunctions.bytes3ToInt;
import static tech.pegasys.artemis.state.BeaconState.BeaconStateHelperFunctions.clamp;
import static tech.pegasys.artemis.state.BeaconState.BeaconStateHelperFunctions.shuffle;
import static tech.pegasys.artemis.state.BeaconState.BeaconStateHelperFunctions.split;

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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import tech.pegasys.artemis.datastructures.beaconchainoperations.AttestationData;
import tech.pegasys.artemis.datastructures.beaconchainoperations.LatestBlockRoots;
import tech.pegasys.artemis.datastructures.beaconchainstate.ForkData;
import tech.pegasys.artemis.datastructures.beaconchainstate.ShardCommittee;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.Validators;

public class BeaconStateTest {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  private BeaconState newState() {
    // Initialize state
    BeaconState state =
        new BeaconState(
            0,
            0,
            new ForkData(UnsignedLong.ZERO, UnsignedLong.ZERO, UnsignedLong.ZERO),
            new Validators(),
            new ArrayList<>(),
            0,
            0,
            Bytes32.ZERO,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            0,
            0,
            0,
            0,
            new ArrayList<>(),
            new LatestBlockRoots(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            Bytes32.ZERO,
            new ArrayList<>());

    // Add validator records
    ArrayList<ValidatorRecord> validators = new ArrayList<>();
    validators.add(
        new ValidatorRecord(
            Bytes48.ZERO,
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

    // Add validator balances
    state.setValidator_balances(new ArrayList<>(Collections.nCopies(5, 100.0)));

    // Create committee
    ArrayList<Integer> new_committee = new ArrayList<>();
    new_committee.add(0);
    new_committee.add(50);
    new_committee.add(100);
    state.getPersistent_committees().add(new_committee);
    state.getPersistent_committees().add(new_committee);

    // Add penalized exit balances
    state.getLatest_penalized_exit_balances().add(10.0);

    // Create shard_committees
    ArrayList<ShardCommittee> new_shard_committees =
        new ArrayList<>(
            Collections.nCopies(
                2,
                new ShardCommittee(
                    UnsignedLong.ZERO,
                    new ArrayList<>(Collections.nCopies(1, 1)),
                    UnsignedLong.ONE)));
    state.setShard_committees_at_slots(
        new ArrayList<>(Collections.nCopies(128, new_shard_committees)));

    return state;
  }

  @Test
  public void
      processDepositValidatorPubkeysDoesNotContainPubkeyAndMinEmptyValidatorIndexIsNegative() {
    BeaconState state = newState();
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
  }

  @Test
  public void processDepositValidatorPubkeysDoesNotContainPubkey() {
    BeaconState state = newState();
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
  }

  @Test
  public void processDepositValidatorPubkeysContainsPubkey() {
    BeaconState state = newState();

    double oldBalance = state.getValidator_balances().get(2);

    assertThat(
            state.process_deposit(
                state,
                Bytes48.leftPad(Bytes.of(200)),
                100,
                EMPTY_SIGNATURE,
                Bytes32.ZERO,
                Bytes32.ZERO,
                Bytes32.ZERO))
        .isEqualTo(2);

    assertThat(state.getValidator_balances().get(2)).isEqualTo(oldBalance + 100.0);
  }

  @Test(expected = AssertionError.class)
  public void getAttestationParticipantsSizesNotEqual() {
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
    byte[] participation_bitfield = Bytes32.ZERO.toArrayUnsafe();

    BeaconState.get_attestation_participants(newState(), attestationData, participation_bitfield);
  }

  @Test
  public void getAttestationParticipantsReturnsEmptyArrayList() {
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

    ArrayList<ShardCommittee> actual =
        BeaconState.get_attestation_participants(
            newState(), attestationData, participation_bitfield);
    ArrayList<ShardCommittee> expected = new ArrayList<>();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void getAttestationParticipantsSuccessful() {
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
  }

  @Test
  public void activateValidatorNotPendingActivation() {
    BeaconState state = newState();
    int validator_index = 0;

    state.activate_validator(validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().longValue())
        .isEqualTo(ACTIVE);
  }

  @Test
  public void activateValidator() {
    BeaconState state = newState();
    int validator_index = 0;

    assertThat(state.getValidator_registry().get(validator_index).getStatus().longValue())
        .isEqualTo(PENDING_ACTIVATION);
    state.activate_validator(validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().longValue())
        .isEqualTo(ACTIVE);
    assertThat(
            state
                .getValidator_registry()
                .get(validator_index)
                .getLatest_status_change_slot()
                .longValue())
        .isEqualTo(state.getSlot());
  }

  @Test
  public void initiateValidatorExitNotActive() {
    BeaconState state = newState();
    int validator_index = 0;

    state.initiate_validator_exit(validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().longValue())
        .isEqualTo(ACTIVATION);
  }

  @Test
  public void initiateValidatorExit() {
    BeaconState state = newState();
    int validator_index = 2;

    state.initiate_validator_exit(validator_index);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().longValue())
        .isEqualTo(ACTIVE_PENDING_EXIT);
    assertThat(
            state
                .getValidator_registry()
                .get(validator_index)
                .getLatest_status_change_slot()
                .longValue())
        .isEqualTo(state.getSlot());
  }

  @Test
  public void exitValidatorPrevStatusExitedWithPenaltyNewStatusExitedWithoutPenalty() {
    BeaconState state = newState();
    int validator_index = 4;

    state.exit_validator(state, validator_index, EXITED_WITHOUT_PENALTY);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().longValue())
        .isEqualTo(EXITED_WITH_PENALTY);
  }

  @Test
  public void exitValidatorPrevStatusExitedWithoutPenaltyNewStatusExitedWithoutPenalty() {
    BeaconState state = newState();
    int validator_index = 3;

    state.exit_validator(state, validator_index, EXITED_WITHOUT_PENALTY);
    assertThat(state.getValidator_registry().get(validator_index).getStatus().longValue())
        .isEqualTo(EXITED_WITHOUT_PENALTY);
    assertThat(
            state
                .getValidator_registry()
                .get(validator_index)
                .getLatest_status_change_slot()
                .longValue())
        .isEqualTo(state.getSlot());
  }

  @Test
  public void exitValidatorPrevStatusExitedWithoutPenaltyNewStatusExitedWithPenalty() {
    BeaconState state = newState();
    int validator_index = 3;

    long before_exit_count = state.getValidator_registry_exit_count();
    double before_balance = state.getValidator_balances().get(validator_index);
    //    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(state, validator_index, EXITED_WITH_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.getValidator_registry_exit_count());
    assertThat(state.getValidator_balances().get(validator_index)).isLessThan(before_balance);
    // TODO: Uncomment this when tree_root_hash is working.
    //    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  public void exitValidatorPrevStatusDidNotExitNewStatusExitedWithPenalty() {
    BeaconState state = newState();
    int validator_index = 2;

    long before_exit_count = state.getValidator_registry_exit_count();
    double before_balance = state.getValidator_balances().get(validator_index);
    //    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(state, validator_index, EXITED_WITH_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.getValidator_registry_exit_count() - 1);
    assertThat(state.getValidator_balances().get(validator_index)).isLessThan(before_balance);
    // TODO: Uncomment this when tree_root_hash is working.
    //    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  public void exitValidatorPrevStatusDidNotExitNewStatusExitedWithoutPenalty() {
    BeaconState state = newState();
    int validator_index = 0;

    long before_exit_count = state.getValidator_registry_exit_count();
    int before_persistent_committees_size =
        state.getPersistent_committees().get(validator_index).size();
    //    Hash before_tip = state.validator_registry_delta_chain_tip;

    state.exit_validator(state, validator_index, EXITED_WITHOUT_PENALTY);

    assertThat(before_exit_count).isEqualTo(state.getValidator_registry_exit_count() - 1);
    assertThat(state.getPersistent_committees().get(validator_index).size())
        .isEqualTo(before_persistent_committees_size - 1);
    // TODO: Uncomment this when tree_root_hash is working.
    //    assertThat(before_tip).isNotEqualTo(state.validator_registry_delta_chain_tip);
  }

  @Test
  public void deepCopyBeaconState() {
    BeaconState state = newState();
    BeaconState deepCopy = BeaconState.deepCopy(state);

    // Test if deepCopy has the same values as the original state
    Gson gson = new Gson();
    String stateJson = gson.toJson(state);
    String deepCopyJson = gson.toJson(deepCopy);
    assertThat(stateJson).isEqualTo(deepCopyJson);

    // Test persistent committees
    ArrayList<Integer> new_committee = new ArrayList<Integer>();
    new_committee.add(20);
    deepCopy.getPersistent_committees().add(new_committee);
    assertThat(deepCopy.getPersistent_committees()).isNotEqualTo(state.getPersistent_committees());

    // Test slot
    state.incrementSlot();
    assertThat(deepCopy.getSlot()).isNotEqualTo(state.getSlot());

    // Test fork_data
    state.setFork_data(new ForkData(UnsignedLong.valueOf(1), UnsignedLong.ONE, UnsignedLong.ONE));
    assertThat(deepCopy.getFork_data().getPre_fork_version())
        .isNotEqualTo(state.getFork_data().getPre_fork_version());

    // Test validator registry
    ArrayList<ValidatorRecord> new_records =
        new ArrayList<ValidatorRecord>(
            Collections.nCopies(
                12,
                new ValidatorRecord(
                    Bytes48.leftPad(Bytes.of(2)),
                    Bytes32.ZERO,
                    Bytes32.ZERO,
                    UnsignedLong.valueOf(PENDING_ACTIVATION),
                    UnsignedLong.valueOf(PENDING_ACTIVATION),
                    UnsignedLong.MAX_VALUE,
                    UnsignedLong.MAX_VALUE,
                    UnsignedLong.ZERO,
                    UnsignedLong.ZERO)));
    deepCopy.setValidator_registry(new_records);
    assertThat(deepCopy.getValidator_registry().get(0).getPubkey())
        .isNotEqualTo(state.getValidator_registry().get(0).getPubkey());
  }

  private Bytes32 hashSrc() {
    Bytes bytes = Bytes.wrap(new byte[] {(byte) 1, (byte) 256, (byte) 65656});
    return Hash.keccak256(bytes);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentsBytes3ToInt() {
    bytes3ToInt(Bytes.wrap(new byte[] {(byte) 0, (byte) 0, (byte) 0}), -1);
  }

  @Test
  public void convertBytes3ToInt() {
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
  public void testShuffle() {
    List<Integer> input = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    ArrayList<Integer> sample = new ArrayList<>(input);

    ArrayList<Integer> actual = shuffle(sample, hashSrc());
    List<Integer> expected_input = Arrays.asList(4, 7, 2, 1, 5, 10, 3, 6, 8, 9);
    ArrayList<Integer> expected = new ArrayList<>(expected_input);
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentTestSplit() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);
    ArrayList<Integer> sample = new ArrayList<>(input);

    split(sample, -1);
  }

  @Test
  public void splitReturnsOneSmallerSizedSplit() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);
    ArrayList<Integer> sample = new ArrayList<>(input);

    ArrayList<ArrayList<Integer>> actual = split(sample, 3);

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
  public void splitReturnsTwoSmallerSizedSplits() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6);
    ArrayList<Integer> sample = new ArrayList<>(input);

    ArrayList<ArrayList<Integer>> actual = split(sample, 3);

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
  public void splitReturnsEquallySizedSplits() {
    List<Integer> input = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);
    ArrayList<Integer> sample = new ArrayList<>(input);

    ArrayList<ArrayList<Integer>> actual = split(sample, 3);

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
  public void clampReturnsMinVal() {
    int actual = clamp(3, 5, 0);
    int expected = 3;
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void clampReturnsMaxVal() {
    int actual = clamp(3, 5, 6);
    int expected = 5;
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void clampReturnsX() {
    int actual = clamp(3, 5, 4);
    int expected = 4;
    assertThat(actual).isEqualTo(expected);
  }
}
