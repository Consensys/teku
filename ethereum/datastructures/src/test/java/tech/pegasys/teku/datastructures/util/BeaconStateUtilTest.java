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

package tech.pegasys.teku.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_next_epoch_boundary;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;
import static tech.pegasys.teku.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Committee;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateUtilTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  void minReturnsMin() {
    UInt64 actual = UInt64.valueOf(13L).min(UInt64.valueOf(12L));
    UInt64 expected = UInt64.valueOf(12L);
    assertEquals(expected, actual);
  }

  @Test
  void minReturnsMinWhenEqual() {
    UInt64 actual = UInt64.valueOf(12L).min(UInt64.valueOf(12L));
    UInt64 expected = UInt64.valueOf(12L);
    assertEquals(expected, actual);
  }

  @Test
  void maxReturnsMax() {
    UInt64 actual = UInt64.valueOf(13L).max(UInt64.valueOf(12L));
    UInt64 expected = UInt64.valueOf(13L);
    assertEquals(expected, actual);
  }

  @Test
  void maxReturnsMaxWhenEqual() {
    UInt64 actual = UInt64.valueOf(13L).max(UInt64.valueOf(13L));
    UInt64 expected = UInt64.valueOf(13L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfSquareNumber() {
    UInt64 actual = BeaconStateUtil.integer_squareroot(UInt64.valueOf(3481L));
    UInt64 expected = UInt64.valueOf(59L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANonSquareNumber() {
    UInt64 actual = BeaconStateUtil.integer_squareroot(UInt64.valueOf(27L));
    UInt64 expected = UInt64.valueOf(5L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANegativeNumber() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BeaconStateUtil.integer_squareroot(UInt64.valueOf(-1L)));
  }

  @Test
  void validateProofOfPossessionReturnsTrueIfTheBLSSignatureIsValidForGivenDepositInputData() {
    Deposit deposit = dataStructureUtil.newDeposits(1).get(0);
    BLSPublicKey pubkey = deposit.getData().getPubkey();
    DepositData depositData = deposit.getData();
    DepositMessage depositMessage =
        new DepositMessage(
            depositData.getPubkey(),
            depositData.getWithdrawal_credentials(),
            depositData.getAmount());
    Bytes32 domain =
        BeaconStateUtil.get_domain(
            createBeaconState(),
            Constants.DOMAIN_DEPOSIT,
            UInt64.fromLongBits(Constants.GENESIS_EPOCH));
    Bytes signing_root = compute_signing_root(depositMessage, domain);

    assertTrue(BLS.verify(pubkey, signing_root, depositData.getSignature()));
  }

  @Test
  void validateProofOfPossessionReturnsFalseIfTheBLSSignatureIsNotValidForGivenDepositInputData() {
    Deposit deposit = dataStructureUtil.newDeposits(1).get(0);
    BLSPublicKey pubkey = BLSPublicKey.random(42);
    DepositData depositData = deposit.getData();
    DepositMessage depositMessage =
        new DepositMessage(
            depositData.getPubkey(),
            depositData.getWithdrawal_credentials(),
            depositData.getAmount());
    Bytes32 domain =
        BeaconStateUtil.get_domain(
            createBeaconState(),
            Constants.DOMAIN_DEPOSIT,
            UInt64.fromLongBits(Constants.GENESIS_EPOCH));
    Bytes signing_root = compute_signing_root(depositMessage, domain);

    assertFalse(BLS.verify(pubkey, signing_root, depositData.getSignature()));
  }

  @Test
  void getTotalBalanceAddsAndReturnsEffectiveTotalBalancesCorrectly() {
    // Data Setup
    BeaconState state = createBeaconState();
    Committee committee = new Committee(UInt64.ONE, Arrays.asList(0, 1, 2));

    // Calculate Expected Results
    UInt64 expectedBalance = UInt64.ZERO;
    for (UInt64 balance : state.getBalances()) {
      if (balance.isLessThan(Constants.MAX_EFFECTIVE_BALANCE)) {
        expectedBalance = expectedBalance.plus(balance);
      } else {
        expectedBalance = expectedBalance.plus(Constants.MAX_EFFECTIVE_BALANCE);
      }
    }

    UInt64 totalBalance = BeaconStateUtil.get_total_balance(state, committee.getCommittee());
    assertEquals(expectedBalance, totalBalance);
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot1() {
    BeaconState beaconState =
        createBeaconState().updated(state -> state.setSlot(UInt64.valueOf(GENESIS_SLOT)));
    assertEquals(
        UInt64.valueOf(Constants.GENESIS_EPOCH), BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot2() {
    BeaconState beaconState =
        createBeaconState()
            .updated(
                state -> state.setSlot(UInt64.valueOf(GENESIS_SLOT + Constants.SLOTS_PER_EPOCH)));
    assertEquals(
        UInt64.valueOf(Constants.GENESIS_EPOCH), BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlotPlusOne() {
    BeaconState beaconState =
        createBeaconState()
            .updated(
                state ->
                    state.setSlot(UInt64.valueOf(GENESIS_SLOT + 2 * Constants.SLOTS_PER_EPOCH)));
    assertEquals(
        UInt64.valueOf(Constants.GENESIS_EPOCH + 1),
        BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetNextEpochReturnsTheEpochPlusOne() {
    BeaconState beaconState =
        createBeaconState().updated(state -> state.setSlot(UInt64.valueOf(GENESIS_SLOT)));
    assertEquals(
        UInt64.valueOf(Constants.GENESIS_EPOCH + 1), BeaconStateUtil.get_next_epoch(beaconState));
  }

  @Test
  void intToBytes() {
    long value = 0x0123456789abcdefL;
    assertEquals(Bytes.EMPTY, BeaconStateUtil.uint_to_bytes(value, 0));
    assertEquals(Bytes.fromHexString("0xef"), BeaconStateUtil.uint_to_bytes(value, 1));
    assertEquals(Bytes.fromHexString("0xefcd"), BeaconStateUtil.uint_to_bytes(value, 2));
    assertEquals(Bytes.fromHexString("0xefcdab89"), BeaconStateUtil.uint_to_bytes(value, 4));
    assertEquals(
        Bytes.fromHexString("0xefcdab8967452301"), BeaconStateUtil.uint_to_bytes(value, 8));
    assertEquals(
        Bytes.fromHexString("0xefcdab89674523010000000000000000"),
        BeaconStateUtil.uint_to_bytes(value, 16));
    assertEquals(
        Bytes.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes(value, 32));
  }

  @Test
  void intToBytes32Long() {
    assertEquals(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes32(0L));
    assertEquals(
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes32(1L));
    assertEquals(
        Bytes32.fromHexString("0xffffffffffffffff000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes32(-1L));
    assertEquals(
        Bytes32.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes32(0x0123456789abcdefL));
  }

  @Test
  void intToBytes32UInt64() {
    assertEquals(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes32(UInt64.ZERO));
    assertEquals(
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes32(UInt64.ONE));
    assertEquals(
        Bytes32.fromHexString("0xffffffffffffffff000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes32(UInt64.MAX_VALUE));
    assertEquals(
        Bytes32.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.uint_to_bytes32(UInt64.valueOf(0x0123456789abcdefL)));
  }

  @Test
  void bytesToInt() {
    assertEquals(UInt64.valueOf(0), BeaconStateUtil.bytes_to_int64(Bytes.fromHexString("0x00")));
    assertEquals(UInt64.valueOf(1), BeaconStateUtil.bytes_to_int64(Bytes.fromHexString("0x01")));
    assertEquals(
        UInt64.valueOf(1),
        BeaconStateUtil.bytes_to_int64(Bytes.fromHexString("0x0100000000000000")));
    assertEquals(
        UInt64.valueOf(0x123456789abcdef0L),
        BeaconStateUtil.bytes_to_int64(Bytes.fromHexString("0xf0debc9a78563412")));
    assertEquals(
        UInt64.fromLongBits(0xffffffffffffffffL),
        BeaconStateUtil.bytes_to_int64(Bytes.fromHexString("0xffffffffffffffff")));
    assertEquals(
        UInt64.fromLongBits(0x0000000000000080L),
        BeaconStateUtil.bytes_to_int64(Bytes.fromHexString("0x8000000000000000")));
    assertEquals(
        UInt64.fromLongBits(0xffffffffffffff7fL),
        BeaconStateUtil.bytes_to_int64(Bytes.fromHexString("0x7fffffffffffffff")));
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_zero() {
    assertThatThrownBy(() -> BeaconStateUtil.isSlotAtNthEpochBoundary(UInt64.ONE, UInt64.ZERO, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_negative() {
    assertThatThrownBy(() -> BeaconStateUtil.isSlotAtNthEpochBoundary(UInt64.ONE, UInt64.ZERO, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_allSlotsFilled(final int n) {
    final UInt64 epochs = UInt64.valueOf(n * 3);
    final UInt64 slots = epochs.times(SLOTS_PER_EPOCH);

    for (int i = 1; i <= slots.intValue(); i++) {
      final boolean expected = i % (n * SLOTS_PER_EPOCH) == 0 && i != 0;

      final UInt64 blockSlot = UInt64.valueOf(i);
      assertThat(BeaconStateUtil.isSlotAtNthEpochBoundary(blockSlot, blockSlot.minus(1), n))
          .describedAs("Block at %d should %sbe at epoch boundary", i, expected ? "" : "not ")
          .isEqualTo(expected);
    }
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  void isSlotAtNthEpochBoundary_withSkippedBlock(final int n) {
    final int nthStartSlot = compute_start_slot_at_epoch(UInt64.valueOf(n)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(nthStartSlot + 1);
    final UInt64 block2Slot = block1Slot.plus(1);
    assertThat(BeaconStateUtil.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(BeaconStateUtil.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_withSkippedEpochs_oneEpochAndSlotSkipped(final int n) {
    final int nthStartSlot = compute_start_slot_at_epoch(UInt64.valueOf(n)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(nthStartSlot + SLOTS_PER_EPOCH + 1);
    final UInt64 block2Slot = block1Slot.plus(1);

    assertThat(BeaconStateUtil.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(BeaconStateUtil.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_withSkippedEpochs_nearlyNEpochsSkipped(final int n) {
    final int startSlotAt2N = compute_start_slot_at_epoch(UInt64.valueOf(n * 2)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(startSlotAt2N - 1);
    final UInt64 block2Slot = block1Slot.plus(1);
    final UInt64 block3Slot = block2Slot.plus(1);

    assertThat(BeaconStateUtil.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(BeaconStateUtil.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isTrue();
    assertThat(BeaconStateUtil.isSlotAtNthEpochBoundary(block3Slot, block2Slot, n)).isFalse();
  }

  public static Stream<Arguments> getNValues() {
    return Stream.of(
        Arguments.of(1), Arguments.of(2), Arguments.of(3), Arguments.of(4), Arguments.of(5));
  }

  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  private BeaconState createBeaconState(
      boolean addToList, UInt64 amount, Validator knownValidator) {
    return BeaconState.createEmpty()
        .updated(
            beaconState -> {
              beaconState.setSlot(dataStructureUtil.randomUInt64());
              beaconState.setFork(
                  new Fork(
                      Constants.GENESIS_FORK_VERSION,
                      Constants.GENESIS_FORK_VERSION,
                      UInt64.valueOf(Constants.GENESIS_EPOCH)));

              List<Validator> validatorList =
                  new ArrayList<>(
                      Arrays.asList(
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator()));
              List<UInt64> balanceList =
                  new ArrayList<>(Collections.nCopies(3, Constants.MAX_EFFECTIVE_BALANCE));

              if (addToList) {
                validatorList.add(knownValidator);
                balanceList.add(amount);
              }

              beaconState
                  .getValidators()
                  .addAll(
                      SSZList.createMutable(
                          validatorList, Constants.VALIDATOR_REGISTRY_LIMIT, Validator.class));
              beaconState
                  .getBalances()
                  .addAll(
                      SSZList.createMutable(
                          balanceList, Constants.VALIDATOR_REGISTRY_LIMIT, UInt64.class));
            });
  }

  // *************** START Shuffling Tests ***************

  // The following are just sanity checks. The real testing is against the official test vectors,
  // elsewhere.

  @Test
  void succeedsWhenGetPermutedIndexReturnsAPermutation() {
    Bytes32 seed = Bytes32.random();
    int listSize = 1000;
    boolean[] done = new boolean[listSize]; // Initialized to false
    for (int i = 0; i < listSize; i++) {
      int idx = CommitteeUtil.compute_shuffled_index(i, listSize, seed);
      assertFalse(done[idx]);
      done[idx] = true;
    }
  }

  // *************** END Shuffling Tests *****************

  @Test
  void processDepositsShouldIgnoreInvalidSignedDeposits() {
    ArrayList<DepositWithIndex> deposits = dataStructureUtil.randomDeposits(3);
    deposits.get(1).getData().setSignature(BLSSignature.empty());
    BeaconState state = initialize_beacon_state_from_eth1(Bytes32.ZERO, UInt64.ZERO, deposits);
    assertEquals(2, state.getValidators().size());
    assertEquals(
        deposits.get(0).getData().getPubkey().toBytesCompressed(),
        state.getValidators().get(0).getPubkey());
    assertEquals(
        deposits.get(2).getData().getPubkey().toBytesCompressed(),
        state.getValidators().get(1).getPubkey());
  }

  @Test
  void ensureVerifyDepositDefaultsToTrue() {
    assertThat(BeaconStateUtil.BLS_VERIFY_DEPOSIT).isTrue();
  }

  @Test
  void compute_next_epoch_boundary_slotAtBoundary() {
    final UInt64 expectedEpoch = UInt64.valueOf(2);
    final UInt64 slot = compute_start_slot_at_epoch(expectedEpoch);

    assertThat(compute_next_epoch_boundary(slot)).isEqualTo(expectedEpoch);
  }

  @Test
  void compute_next_epoch_boundary_slotPriorToBoundary() {
    final UInt64 expectedEpoch = UInt64.valueOf(2);
    final UInt64 slot = compute_start_slot_at_epoch(expectedEpoch).minus(1);

    assertThat(compute_next_epoch_boundary(slot)).isEqualTo(expectedEpoch);
  }

  @Test
  void getCurrentDutyDependentRoot_genesisStateReturnsFinalizedCheckpointRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT));
    assertThat(BeaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(new BeaconBlock(state.hash_tree_root()).getRoot());
  }

  @Test
  void getCurrentDutyDependentRoot_returnsGenesisBlockDuringEpochZero() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + 3));
    assertThat(BeaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getCurrentDutyDependentRoot_returnsBlockRootAtLastSlotOfPriorEpoch() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + SLOTS_PER_EPOCH + 3));
    assertThat(BeaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get((int) (GENESIS_SLOT + SLOTS_PER_EPOCH - 1)));
  }

  @Test
  void getPreviousDutyDependentRoot_genesisStateReturnsFinalizedCheckpointRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT));
    assertThat(BeaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(new BeaconBlock(state.hash_tree_root()).getRoot());
  }

  @Test
  void getPreviousDutyDependentRoot_returnsGenesisBlockDuringEpochZero() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + 3));
    assertThat(BeaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getPreviousDutyDependentRoot_returnsGenesisBlockDuringEpochOne() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + SLOTS_PER_EPOCH + 3));
    assertThat(BeaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getCurrentDutyDependentRoot_returnsBlockRootAtLastSlotOfTwoEpochsAgo() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(GENESIS_SLOT + SLOTS_PER_EPOCH * 2 + 3));
    assertThat(BeaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get((int) (GENESIS_SLOT + SLOTS_PER_EPOCH - 1)));
  }
}
