/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.core.BlockProcessorUtil;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.fuzz.input.DepositFuzzInput;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

@ExtendWith(BouncyCastleExtension.class)
class FuzzUtilTest {

  // Basic sanity tests for Fuzzing Harnesses
  // NOTE: for the purposes of this class, we don't care so much that operation is
  // correct/equivalent according to the spec
  // (the reference tests cover this), but that the Fuzz harness is equivalent to the behavior of
  // the internal process.
  // e.g. These tests don't care whether process_deposits is correct, but that the harness correctly
  // uses process_deposits.
  // Will this pick things up, or is it basically implementing the same logic twice?
  // TODO confirm

  private final FuzzUtil fuzzUtil = new FuzzUtil(true, true);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  // *************** START Deposit Tests *****************

  @Test
  void shouldProcessRandomDepositEquivalently() {
    // TODO if the randomBeaconState might not be "valid", we want to use a fixed BeaconState that
    // is correct
    // TODO check: unlikely that this is a valid deposit for the beaconState?
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
    SSZList<DepositWithIndex> deposits = dataStructureUtil.newDeposits(1);
    Deposit deposit = deposits.get(0);
    DepositFuzzInput input = new DepositFuzzInput(fromBeaconState(beaconState), deposit);
    byte[] rawInput = SimpleOffsetSerializer.serialize(input).toArrayUnsafe();
    Optional<byte[]> actual = fuzzUtil.fuzzDeposit(rawInput);

    try {
      BeaconState postState =
          beaconState.updated(
              state -> {
                BlockProcessorUtil.process_deposits(state, deposits);
              });
      byte[] expected = SimpleOffsetSerializer.serialize(postState).toArrayUnsafe();
      assertThat(actual)
          .hasValueSatisfying(
              b -> {
                assertThat(b).isEqualTo(expected);
              });

    } catch (BlockProcessingException e) {
      // actual should be empty
      assertThat(actual).isEmpty();
    }
  }

  // *************** END Deposit Tests *******************

  // *************** START Shuffling Tests ***************

  @Test
  void shuffleInsufficientInput() {
    byte[] emptyInput = new byte[0];
    assertThat(fuzzUtil.fuzzShuffle(emptyInput)).isEmpty();
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(15).toArrayUnsafe())).isEmpty();
    // minimum length is 34
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(33).toArrayUnsafe())).isEmpty();
  }

  @Test
  void shuffleSufficientInput() {
    // minimum length is 34, and should succeed
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(34).toArrayUnsafe())).isPresent();
    assertThat(fuzzUtil.fuzzShuffle(Bytes.random(80).toArrayUnsafe())).isPresent();

    // first 2 bytes (little endian) % 100 provide the count, the rest the seed
    // 1000 = 16 (little endian)
    byte[] input =
        Bytes.fromHexString(
                "0x10000000000000000000000000000000000000000000000000000000000000000000")
            .toArrayUnsafe();
    assertThat(fuzzUtil.fuzzShuffle(input))
        .hasValueSatisfying(
            b -> {
              assertThat(b).hasSize(16 * Long.BYTES);
            });
  }

  // *************** END Shuffling Tests *****************

  // *************** Helpers *****************************

  // Because Fuzz input constructors want a BeaconStateImpl (for introspection)
  // but the DataStructureUtil functions returns a BeaconState
  private BeaconStateImpl fromBeaconState(BeaconState state) {
    return new BeaconStateImpl(
        state.getGenesis_time(),
        state.getGenesis_validators_root(),
        state.getSlot(),
        state.getFork(),
        state.getLatest_block_header(),
        state.getBlock_roots(),
        state.getState_roots(),
        state.getHistorical_roots(),
        state.getEth1_data(),
        state.getEth1_data_votes(),
        state.getEth1_deposit_index(),
        state.getValidators(),
        state.getBalances(),
        state.getRandao_mixes(),
        state.getSlashings(),
        state.getPrevious_epoch_attestations(),
        state.getCurrent_epoch_attestations(),
        state.getJustification_bits(),
        state.getPrevious_justified_checkpoint(),
        state.getCurrent_justified_checkpoint(),
        state.getFinalized_checkpoint());
  }

  /*// copied from BlockProcessorUtilTest
  // TODO move from both into DataStructureUtil?
  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  private BeaconState createBeaconState(UnsignedLong amount, Validator knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  private BeaconState createBeaconState(
      boolean addToList, UnsignedLong amount, Validator knownValidator) {
    return BeaconState.createEmpty()
        .updated(
            beaconState -> {
              beaconState.setSlot(dataStructureUtil.randomUnsignedLong());
              beaconState.setFork(
                  new Fork(
                      Constants.GENESIS_FORK_VERSION,
                      Constants.GENESIS_FORK_VERSION,
                      UnsignedLong.valueOf(Constants.GENESIS_EPOCH)));

              SSZMutableList<Validator> validatorList =
                  SSZList.createMutable(
                      Arrays.asList(
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator()),
                      Constants.VALIDATOR_REGISTRY_LIMIT,
                      Validator.class);
              SSZMutableList<UnsignedLong> balanceList =
                  SSZList.createMutable(
                      Arrays.asList(
                          dataStructureUtil.randomUnsignedLong(),
                          dataStructureUtil.randomUnsignedLong(),
                          dataStructureUtil.randomUnsignedLong()),
                      Constants.VALIDATOR_REGISTRY_LIMIT,
                      UnsignedLong.class);

              if (addToList) {
                validatorList.add(knownValidator);
                balanceList.add(amount);
              }

              beaconState.getValidators().addAll(validatorList);
              beaconState.getBalances().addAll(balanceList);
            });
  }*/
}
