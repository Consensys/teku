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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@ExtendWith(BouncyCastleExtension.class)
public class BeaconStateUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private final BeaconStateUtil beaconStateUtil = genesisSpec.getBeaconStateUtil();
  private final SpecConfig specConfig = spec.atSlot(UInt64.ZERO).getConfig();
  private final long SLOTS_PER_EPOCH = specConfig.getSlotsPerEpoch();

  @Test
  void getPreviousDutyDependentRoot_genesisStateReturnsFinalizedCheckpointRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState(GENESIS_SLOT);
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(BeaconBlock.fromGenesisState(spec, state).getRoot());
  }

  @Test
  void getPreviousDutyDependentRoot_returnsGenesisBlockDuringEpochZero() {
    final BeaconState state = dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().getElement(0));
  }

  @Test
  void getPreviousDutyDependentRoot_returnsGenesisBlockDuringEpochOne() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(SLOTS_PER_EPOCH).plus(3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().getElement(0));
  }

  @Test
  void getCurrentDutyDependentRoot_returnsBlockRootAtLastSlotOfTwoEpochsAgo() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(SLOTS_PER_EPOCH * 2).plus(3));
    assertThat(beaconStateUtil.getPreviousDutyDependentRoot(state))
        .isEqualTo(
            state
                .getBlock_roots()
                .getElement((int) GENESIS_SLOT.plus(SLOTS_PER_EPOCH).decrement().longValue()));
  }

  @Test
  void compute_next_epoch_boundary_slotAtBoundary() {
    final UInt64 expectedEpoch = UInt64.valueOf(2);
    final UInt64 slot = spec.computeStartSlotAtEpoch(expectedEpoch);

    assertThat(beaconStateUtil.computeNextEpochBoundary(slot)).isEqualTo(expectedEpoch);
  }

  @Test
  void compute_next_epoch_boundary_slotPriorToBoundary() {
    final UInt64 expectedEpoch = UInt64.valueOf(2);
    final UInt64 slot = spec.computeStartSlotAtEpoch(expectedEpoch).minus(1);

    assertThat(beaconStateUtil.computeNextEpochBoundary(slot)).isEqualTo(expectedEpoch);
  }

  @Test
  void getCurrentDutyDependentRoot_genesisStateReturnsFinalizedCheckpointRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState(GENESIS_SLOT);
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(BeaconBlock.fromGenesisState(spec, state).getRoot());
  }

  @Test
  void getCurrentDutyDependentRoot_returnsGenesisBlockDuringEpochZero() {
    final BeaconState state = dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(3));
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(state.getBlock_roots().getElement(0));
  }

  @Test
  void getCurrentDutyDependentRoot_returnsBlockRootAtLastSlotOfPriorEpoch() {
    final BeaconState state =
        dataStructureUtil.randomBeaconState(GENESIS_SLOT.plus(SLOTS_PER_EPOCH).plus(3));
    assertThat(beaconStateUtil.getCurrentDutyDependentRoot(state))
        .isEqualTo(
            state
                .getBlock_roots()
                .getElement((int) GENESIS_SLOT.plus(SLOTS_PER_EPOCH).decrement().longValue()));
  }

  private BeaconState createBeaconState() {
    return new BeaconStateTestBuilder(dataStructureUtil)
        .forkVersion(specConfig.getGenesisForkVersion())
        .validator(dataStructureUtil.randomValidator())
        .validator(dataStructureUtil.randomValidator())
        .validator(dataStructureUtil.randomValidator())
        .build();
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
        genesisSpec
            .beaconStateAccessors()
            .getDomain(createBeaconState(), Domain.DEPOSIT, GENESIS_EPOCH);
    Bytes signing_root = genesisSpec.miscHelpers().computeSigningRoot(depositMessage, domain);

    assertFalse(BLS.verify(pubkey, signing_root, depositData.getSignature()));
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_zero() {
    assertThatThrownBy(
            () ->
                tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.isSlotAtNthEpochBoundary(
                    UInt64.ONE, UInt64.ZERO, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @Test
  public void isSlotAtNthEpochBoundary_invalidNParameter_negative() {
    assertThatThrownBy(
            () ->
                tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.isSlotAtNthEpochBoundary(
                    UInt64.ONE, UInt64.ZERO, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Parameter n must be greater than 0");
  }

  @Test
  public void
      getAttestersTotalEffectiveBalance_calculatesTotalEffectiveBalanceInAllCommitteesForSlot() {
    final BeaconState state =
        new BeaconStateTestBuilder(dataStructureUtil)
            .slot(5)

            // Not quite enough validators to have one in every slot
            .activeValidator(UInt64.valueOf(3200000000L))
            .activeValidator(UInt64.valueOf(3200000000L))
            .activeValidator(UInt64.valueOf(3200000000L))
            .activeValidator(UInt64.valueOf(2000000000L))
            .activeValidator(UInt64.valueOf(1600000000L))
            .activeValidator(UInt64.valueOf(1800000000L))
            .build();

    // Randao seed is fixed for state so we know the committee allocations will be the same
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(0)))
        .isEqualTo(UInt64.ZERO);
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(1)))
        .isEqualTo(UInt64.valueOf(3200000000L));
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(2)))
        .isEqualTo(UInt64.valueOf(2000000000L));
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(3)))
        .isEqualTo(UInt64.valueOf(1800000000L));
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(4)))
        .isEqualTo(UInt64.ZERO);
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(5)))
        .isEqualTo(UInt64.valueOf(3200000000L));
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(6)))
        .isEqualTo(UInt64.valueOf(1600000000L));
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(7)))
        .isEqualTo(UInt64.valueOf(3200000000L));

    assertAttestersBalancesSumToTotalBalancesOverEpoch(state);
  }

  @Test
  public void getAttestersTotalEffectiveBalance_shouldCombinedAllCommitteesForSlot() {
    final BeaconStateTestBuilder stateBuilder =
        new BeaconStateTestBuilder(dataStructureUtil).slot(5);
    for (int i = 0;
        i < specConfig.getSlotsPerEpoch() * specConfig.getTargetAggregatorsPerCommittee() * 2;
        i++) {
      stateBuilder.activeValidator(
          UInt64.valueOf(i).times(specConfig.getEffectiveBalanceIncrement()));
    }
    final BeaconState state = stateBuilder.build();
    assertThat(genesisSpec.beaconStateAccessors().getCommitteeCountPerSlot(state, UInt64.ZERO))
        .isGreaterThan(UInt64.ZERO);

    // Randao seed is fixed for state so we know the committee allocations will be the same
    assertThat(beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(0)))
        .isEqualTo(UInt64.valueOf(4394).times(specConfig.getEffectiveBalanceIncrement()));

    assertAttestersBalancesSumToTotalBalancesOverEpoch(state);
  }

  @Test
  void getAttestersTotalEffectiveBalance_shouldCalculateTotalsFromEarlierEpoch() {
    final BeaconState state =
        new BeaconStateTestBuilder(dataStructureUtil)
            .slot(50)

            // Not quite enough validators to have one in every slot
            .activeValidator(UInt64.valueOf(3200000000L))
            .activeValidator(UInt64.valueOf(3200000000L))
            .activeValidator(UInt64.valueOf(3200000000L))
            .activeValidator(UInt64.valueOf(2000000000L))
            .activeValidator(UInt64.valueOf(1600000000L))
            .activeValidator(UInt64.valueOf(1800000000L))
            .build();
    final UInt64 result = beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.ONE);
    assertThat(result).isEqualTo(UInt64.valueOf(3200000000L));
    assertAttestersBalancesSumToTotalBalancesOverEpoch(state);
  }

  /**
   * Since every active validator attests once per epoch, the total sum of attester effective
   * balances across each epoch in the slot should be equal to the total active balance for the
   * sate.
   */
  private void assertAttestersBalancesSumToTotalBalancesOverEpoch(final BeaconState state) {
    final UInt64 expectedTotalBalance =
        genesisSpec.beaconStateAccessors().getTotalActiveBalance(state);
    UInt64 actualTotalBalance = UInt64.ZERO;
    for (int i = 0; i < specConfig.getSlotsPerEpoch(); i++) {
      actualTotalBalance =
          actualTotalBalance.plus(
              beaconStateUtil.getAttestersTotalEffectiveBalance(state, UInt64.valueOf(i)));
    }
    assertThat(actualTotalBalance).isEqualTo(expectedTotalBalance);
  }

  @Test
  void getAttestersTotalEffectiveBalance_shouldRejectRequestFromBeyondLookAheadPeriod() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.ONE);
    final UInt64 epoch3Start = spec.computeStartSlotAtEpoch(UInt64.valueOf(3));
    assertThatThrownBy(() -> beaconStateUtil.getAttestersTotalEffectiveBalance(state, epoch3Start))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
