/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.spec.constants.SpecConstants.GENESIS_EPOCH;
import static tech.pegasys.teku.spec.constants.SpecConstants.GENESIS_SLOT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Committee;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.StubSpecProvider;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

@ExtendWith(BouncyCastleExtension.class)
public class BeaconStateUtilTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final SpecProvider specProvider = StubSpecProvider.create();
  private final BeaconStateUtil beaconStateUtil =
      specProvider.atSlot(UInt64.ZERO).getBeaconStateUtil();
  private final SpecConstants specConstants = specProvider.atSlot(UInt64.ZERO).getConstants();
  private final long SLOTS_PER_EPOCH = specConstants.getSlotsPerEpoch();

  @BeforeEach
  void setup() {}

  @Test
  void succeedsWhenGetNextEpochReturnsTheEpochPlusOne() {
    BeaconState beaconState = createBeaconState().updated(state -> state.setSlot(GENESIS_SLOT));
    assertEquals(GENESIS_EPOCH.increment(), beaconStateUtil.getNextEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot1() {
    BeaconState beaconState = createBeaconState().updated(state -> state.setSlot(GENESIS_SLOT));
    assertEquals(GENESIS_EPOCH, beaconStateUtil.getPreviousEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot2() {
    BeaconState beaconState =
        createBeaconState()
            .updated(state -> state.setSlot(GENESIS_SLOT.plus(specConstants.getSlotsPerEpoch())));
    assertEquals(GENESIS_EPOCH, beaconStateUtil.getPreviousEpoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlotPlusOne() {
    BeaconState beaconState =
        createBeaconState()
            .updated(
                state -> state.setSlot(GENESIS_SLOT.plus(2 * specConstants.getSlotsPerEpoch())));
    assertEquals(GENESIS_EPOCH.increment(), beaconStateUtil.getPreviousEpoch(beaconState));
  }

  @Test
  void getPreviousDutyDependentRoot_genesisStateReturnsFinalizedCheckpointRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState(GENESIS_SLOT);
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(BeaconBlock.fromGenesisState(state).getRoot());
  }

  @Test
  void getPreviousDutyDependentRoot_returnsGenesisBlockDuringEpochZero() {
    final BeaconState state = dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getPreviousDutyDependentRoot_returnsGenesisBlockDuringEpochOne() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(SLOTS_PER_EPOCH).plus(3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getCurrentDutyDependentRoot_returnsBlockRootAtLastSlotOfTwoEpochsAgo() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(SLOTS_PER_EPOCH * 2).plus(3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(
            state
                .getBlock_roots()
                .get((int) GENESIS_SLOT.plus(SLOTS_PER_EPOCH).decrement().longValue()));
  }

  @Test
  void compute_next_epoch_boundary_slotAtBoundary() {
    final UInt64 expectedEpoch = UInt64.valueOf(2);
    final UInt64 slot = beaconStateUtil.computeStartSlotAtEpoch(expectedEpoch);

    assertThat(beaconStateUtil.computeNextEpochBoundary(slot)).isEqualTo(expectedEpoch);
  }

  @Test
  void compute_next_epoch_boundary_slotPriorToBoundary() {
    final UInt64 expectedEpoch = UInt64.valueOf(2);
    final UInt64 slot = beaconStateUtil.computeStartSlotAtEpoch(expectedEpoch).minus(1);

    assertThat(beaconStateUtil.computeNextEpochBoundary(slot)).isEqualTo(expectedEpoch);
  }

  @Test
  void getCurrentDutyDependentRoot_genesisStateReturnsFinalizedCheckpointRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState(GENESIS_SLOT);
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(BeaconBlock.fromGenesisState(state).getRoot());
  }

  @Test
  void getCurrentDutyDependentRoot_returnsGenesisBlockDuringEpochZero() {
    final BeaconState state = dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(3));
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().get(0));
  }

  @Test
  void getCurrentDutyDependentRoot_returnsBlockRootAtLastSlotOfPriorEpoch() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(SLOTS_PER_EPOCH).plus(3));
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(
            state
                .getBlock_roots()
                .get((int) GENESIS_SLOT.plus(SLOTS_PER_EPOCH).decrement().longValue()));
  }

  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  @Test
  void getTotalBalanceAddsAndReturnsEffectiveTotalBalancesCorrectly() {
    // Data Setup
    BeaconState state = createBeaconState();
    Committee committee = new Committee(UInt64.ONE, Arrays.asList(0, 1, 2));

    // Calculate Expected Results
    UInt64 expectedBalance = UInt64.ZERO;
    for (UInt64 balance : state.getBalances()) {
      if (balance.isLessThan(specConstants.getMaxEffectiveBalance())) {
        expectedBalance = expectedBalance.plus(balance);
      } else {
        expectedBalance = expectedBalance.plus(specConstants.getMaxEffectiveBalance());
      }
    }

    UInt64 totalBalance = beaconStateUtil.getTotalBalance(state, committee.getCommittee());
    assertEquals(expectedBalance, totalBalance);
  }

  @Test
  void validateProofOfPossessionReturnsFalseIfTheBLSSignatureIsNotValidForGivenDepositInputData() {
    Deposit deposit = dataStructureUtil.newDeposits(1).get(0);
    BLSPublicKey pubkey = BLSTestUtil.randomPublicKey(42);
    DepositData depositData = deposit.getData();
    DepositMessage depositMessage =
        new DepositMessage(
            depositData.getPubkey(),
            depositData.getWithdrawal_credentials(),
            depositData.getAmount());
    Bytes32 domain =
        beaconStateUtil.getDomain(
            createBeaconState(), specConstants.getDomainDeposit(), GENESIS_EPOCH);
    Bytes signing_root = beaconStateUtil.computeSigningRoot(depositMessage, domain);

    assertFalse(BLS.verify(pubkey, signing_root, depositData.getSignature()));
  }

  @Test
  void sqrtOfSquareNumber() {
    UInt64 actual = beaconStateUtil.integerSquareRoot(UInt64.valueOf(3481L));
    UInt64 expected = UInt64.valueOf(59L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANonSquareNumber() {
    UInt64 actual = beaconStateUtil.integerSquareRoot(UInt64.valueOf(27L));
    UInt64 expected = UInt64.valueOf(5L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANegativeNumber() {
    assertThrows(
        IllegalArgumentException.class,
        () -> beaconStateUtil.integerSquareRoot(UInt64.valueOf(-1L)));
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_zero() {
    assertThatThrownBy(
            () ->
                tech.pegasys.teku.datastructures.util.BeaconStateUtil.isSlotAtNthEpochBoundary(
                    UInt64.ONE, UInt64.ZERO, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_negative() {
    assertThatThrownBy(
            () ->
                tech.pegasys.teku.datastructures.util.BeaconStateUtil.isSlotAtNthEpochBoundary(
                    UInt64.ONE, UInt64.ZERO, -1))
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
      assertThat(
              tech.pegasys.teku.datastructures.util.BeaconStateUtil.isSlotAtNthEpochBoundary(
                  blockSlot, blockSlot.minus(1), n))
          .describedAs("Block at %d should %sbe at epoch boundary", i, expected ? "" : "not ")
          .isEqualTo(expected);
    }
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  void isSlotAtNthEpochBoundary_withSkippedBlock(final int n) {
    final int nthStartSlot = beaconStateUtil.computeStartSlotAtEpoch(UInt64.valueOf(n)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(nthStartSlot + 1);
    final UInt64 block2Slot = block1Slot.plus(1);
    assertThat(beaconStateUtil.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(beaconStateUtil.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_withSkippedEpochs_oneEpochAndSlotSkipped(final int n) {
    final int nthStartSlot = beaconStateUtil.computeStartSlotAtEpoch(UInt64.valueOf(n)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(nthStartSlot + SLOTS_PER_EPOCH + 1);
    final UInt64 block2Slot = block1Slot.plus(1);

    assertThat(beaconStateUtil.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(beaconStateUtil.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isFalse();
  }

  @ParameterizedTest(name = "n={0}")
  @MethodSource("getNValues")
  public void isSlotAtNthEpochBoundary_withSkippedEpochs_nearlyNEpochsSkipped(final int n) {
    final int startSlotAt2N =
        beaconStateUtil.computeStartSlotAtEpoch(UInt64.valueOf(n * 2)).intValue();

    final UInt64 genesisSlot = UInt64.ZERO;
    final UInt64 block1Slot = UInt64.valueOf(startSlotAt2N - 1);
    final UInt64 block2Slot = block1Slot.plus(1);
    final UInt64 block3Slot = block2Slot.plus(1);

    assertThat(beaconStateUtil.isSlotAtNthEpochBoundary(block1Slot, genesisSlot, n)).isTrue();
    assertThat(beaconStateUtil.isSlotAtNthEpochBoundary(block2Slot, block1Slot, n)).isTrue();
    assertThat(beaconStateUtil.isSlotAtNthEpochBoundary(block3Slot, block2Slot, n)).isFalse();
  }

  public static Stream<Arguments> getNValues() {
    return Stream.of(
        Arguments.of(1), Arguments.of(2), Arguments.of(3), Arguments.of(4), Arguments.of(5));
  }

  private BeaconState createBeaconState(
      boolean addToList, UInt64 amount, Validator knownValidator) {
    return BeaconState.createEmpty()
        .updated(
            beaconState -> {
              beaconState.setSlot(dataStructureUtil.randomUInt64());
              beaconState.setFork(
                  new Fork(
                      specConstants.getGenesisForkVersion(),
                      specConstants.getGenesisForkVersion(),
                      GENESIS_EPOCH));

              List<Validator> validatorList =
                  new ArrayList<>(
                      Arrays.asList(
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator()));
              List<UInt64> balanceList =
                  new ArrayList<>(Collections.nCopies(3, specConstants.getMaxEffectiveBalance()));

              if (addToList) {
                validatorList.add(knownValidator);
                balanceList.add(amount);
              }

              beaconState
                  .getValidators()
                  .addAll(
                      SSZList.createMutable(
                          validatorList,
                          specConstants.getValidatorRegistryLimit(),
                          Validator.class));
              beaconState
                  .getBalances()
                  .addAll(
                      SSZList.createMutable(
                          balanceList, specConstants.getValidatorRegistryLimit(), UInt64.class));
            });
  }
}
