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
import static tech.pegasys.artemis.datastructures.Constants.ACTIVATION_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.LATEST_RANDAO_MIXES_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.MIN_SEED_LOOKAHEAD;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.generate_seed;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_active_index_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_initial_beacon_state;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes32;
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
import net.consensys.cava.crypto.Hash;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateTest {

  private BeaconState newState(int numDeposits) {

    try {

      // Initialize state
      BeaconStateWithCache state = new BeaconStateWithCache();
      get_initial_beacon_state(
          state,
          randomDeposits(numDeposits),
          UnsignedLong.ZERO,
          new Eth1Data(Bytes32.ZERO, Bytes32.ZERO));

      return state;
    } catch (Exception e) {
      fail("get_initial_beacon_state() failed");
      return null;
    }
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
        .isEqualTo(UnsignedLong.valueOf(GENESIS_EPOCH + 1 + ACTIVATION_EXIT_DELAY));
  }

  @Test
  void initiateValidatorExitNotActive() {
    BeaconState state = newState(5);
    int validator_index = 0;

    BeaconStateUtil.initiate_validator_exit(state, validator_index);
    assertThat(state.getValidator_registry().get(validator_index).hasInitiatedExit())
        .isEqualTo(true);
  }

  @Test
  void initiateValidatorExit() {
    BeaconState state = newState(5);
    int validator_index = 2;

    BeaconStateUtil.initiate_validator_exit(state, validator_index);
    assertThat(state.getValidator_registry().get(validator_index).hasInitiatedExit())
        .isEqualTo(true);
  }

  @Test
  void deepCopyBeaconState() {
    BeaconStateWithCache state = (BeaconStateWithCache) newState(1);
    BeaconState deepCopy = BeaconStateWithCache.deepCopy(state);

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
                    BLSPublicKey.empty(),
                    Bytes32.ZERO,
                    UnsignedLong.ZERO,
                    UnsignedLong.valueOf(GENESIS_EPOCH),
                    UnsignedLong.ZERO,
                    false,
                    false)));
    deepCopy.setValidator_registry(new_records);
    assertThat(deepCopy.getValidator_registry().get(0).getPubkey())
        .isNotEqualTo(state.getValidator_registry().get(0).getPubkey());
  }

  private Bytes32 hashSrc() {
    Bytes bytes = Bytes.wrap(new byte[] {(byte) 1, (byte) 256, (byte) 65656});
    Security.addProvider(new BouncyCastleProvider());
    return Hash.keccak256(bytes);
  }

  /* TODO: reinstate test
    @Test
    @Disabled
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
  */

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
  void getRandaoMixThrowsExceptions() {
    BeaconState state = newState(5);
    state.setSlot(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH * SLOTS_PER_EPOCH));
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
    state.setSlot(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH * SLOTS_PER_EPOCH));
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
    state.setSlot(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH * SLOTS_PER_EPOCH));
    assertThat(get_current_epoch(state).compareTo(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)))
        .isEqualTo(0);
    List<Bytes32> latest_randao_mixes = state.getLatest_randao_mixes();
    latest_randao_mixes.set(ACTIVATION_EXIT_DELAY + 1, Bytes32.fromHexString("0x029a"));

    UnsignedLong epoch = UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY + MIN_SEED_LOOKAHEAD + 1);
    Bytes32 randao_mix =
        get_randao_mix(state, epoch.minus(UnsignedLong.valueOf(MIN_SEED_LOOKAHEAD)));
    assertThat(randao_mix).isEqualTo(Bytes32.fromHexString("0x029a"));
    try {
      Security.addProvider(new BouncyCastleProvider());
      assertThat(generate_seed(state, epoch))
          .isEqualTo(
              Hash.keccak256(
                  Bytes.wrap(
                      Bytes32.fromHexString("0x029a"),
                      get_active_index_root(state, epoch),
                      int_to_bytes32(epoch))));
    } catch (IllegalStateException e) {
      fail(e.toString());
    }
  }

  @Test
  void roundtripSSZ() {
    BeaconState state = newState(1);
    Bytes sszBeaconBlockBytes = state.toBytes();
    assertEquals(state, BeaconState.fromBytes(sszBeaconBlockBytes));
  }
}
