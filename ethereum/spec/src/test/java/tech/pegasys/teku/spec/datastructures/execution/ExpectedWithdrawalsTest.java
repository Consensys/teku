/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExpectedWithdrawalsTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @Test
  void bellatrixExpectedWithdrawals() {
    spec = TestSpecFactory.createMinimalBellatrix();
    dataStructureUtil = new DataStructureUtil(spec);
    final ExpectedWithdrawals expectedWithdrawals =
        spec.getGenesisSpec()
            .getBlockProcessor()
            .getExpectedWithdrawals(dataStructureUtil.randomBeaconState());
    assertThat(expectedWithdrawals).isEqualTo(ExpectedWithdrawals.NOOP);
  }

  @Test
  void capellaExpectedWithdrawals() {
    spec = TestSpecFactory.createMinimalCapella();
    dataStructureUtil = new DataStructureUtil(spec);
    final UInt64 minActivationBalance = spec.getGenesisSpecConfig().getMaxEffectiveBalance();
    final long excessBalance = 1024000L;
    final BeaconState preState =
        new BeaconStateTestBuilder(dataStructureUtil)
            .activeEth1Validator(minActivationBalance.plus(excessBalance))
            .build();
    final ExpectedWithdrawals withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    assertThat(withdrawals.getWithdrawalList().get(0).getAmount())
        .isEqualTo(UInt64.valueOf(1024000));
    assertThat(withdrawals.getPartialWithdrawalCount()).isEqualTo(0);
  }

  @Test
  void getPartiallyWithdrawnBalance_returnsZeroIfNotSet() {
    spec = TestSpecFactory.createMinimalElectra();
    final List<Withdrawal> withdrawalList = List.of();
    assertThat(ExpectedWithdrawals.getPartiallyWithdrawnBalance(withdrawalList, ZERO))
        .isEqualTo(ZERO);
  }

  @Test
  void getPartiallyWithdrawnBalance_returnsCurrentValue() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final UInt64 oneEth = UInt64.valueOf(1_000_000_000L);
    final Bytes20 b = dataStructureUtil.randomBytes20();
    final WithdrawalSchema schema = dataStructureUtil.randomWithdrawal().getSchema();
    final List<Withdrawal> withdrawalList =
        List.of(schema.create(ZERO, ZERO, b, oneEth), schema.create(ONE, ZERO, b, oneEth));
    assertThat(ExpectedWithdrawals.getPartiallyWithdrawnBalance(withdrawalList, ZERO))
        .isEqualTo(oneEth.times(2));
  }

  @Test
  void getPartiallyWithdrawnBalance_returnsCurrentValueSpecificToValidator() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final UInt64 oneEth = UInt64.valueOf(1_000_000_000L);
    final Bytes20 b = dataStructureUtil.randomBytes20();
    final WithdrawalSchema schema = dataStructureUtil.randomWithdrawal().getSchema();
    final List<Withdrawal> withdrawalList =
        List.of(schema.create(ZERO, ZERO, b, oneEth), schema.create(ONE, ONE, b, oneEth));
    assertThat(ExpectedWithdrawals.getPartiallyWithdrawnBalance(withdrawalList, ZERO))
        .isEqualTo(oneEth);
  }

  @Test
  void getEthAddressFromWithdrawalCredentials_extracts_address() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final Eth1Address address = dataStructureUtil.randomEth1Address();
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withdrawalCredentials(
                Bytes32.fromHexString("0x01000000000000" + address.toUnprefixedHexString()))
            .build();
    assertThat(
            ExpectedWithdrawals.getEthAddressFromWithdrawalCredentials(validator)
                .toUnprefixedHexString())
        .isEqualToIgnoringCase(address.toUnprefixedHexString());
  }

  @Test
  void createFromInvalidStateReturnsNoop() {
    spec = TestSpecFactory.createMinimalBellatrix();
    dataStructureUtil = new DataStructureUtil(spec);
    assertThat(
            ExpectedWithdrawals.create(
                dataStructureUtil.randomBeaconState(),
                spec.getGenesisSchemaDefinitions(),
                spec.getGenesisSpec().miscHelpers(),
                spec.getGenesisSpecConfig(),
                spec.getGenesisSpec().predicates()))
        .isEqualTo(ExpectedWithdrawals.NOOP);
  }

  @Test
  void electraExpectedWithdrawals() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 electraMaxBalance = specConfigElectra.getMaxEffectiveBalance();
    final long excessBalance = 1024000L;
    final long partialWithdrawalBalance = 10241024L;

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeEth1Validator(UInt64.THIRTY_TWO_ETH.plus(excessBalance))
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(1, electraMaxBalance.plus(partialWithdrawalBalance))
                .build());

    final ExpectedWithdrawals withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);

    assertThat(withdrawals.getWithdrawalList().get(0).getAmount())
        .isEqualTo(UInt64.valueOf(partialWithdrawalBalance));
    assertThat(withdrawals.getWithdrawalList().get(1).getAmount())
        .isEqualTo(UInt64.valueOf(excessBalance));
    assertThat(withdrawals.getPartialWithdrawalCount()).isEqualTo(1);
    final MutableBeaconStateElectra mutableBeaconStateElectra =
        MutableBeaconStateElectra.required(preState.createWritableCopy());

    withdrawals.processWithdrawalsUnchecked(
        mutableBeaconStateElectra,
        SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions()),
        spec.getGenesisSpec().beaconStateMutators(),
        SpecConfigElectra.required(spec.getGenesisSpecConfig()));
    assertThat(mutableBeaconStateElectra.getPendingPartialWithdrawals().size()).isEqualTo(0);

    assertThat(mutableBeaconStateElectra.getNextWithdrawalIndex()).isEqualTo(UInt64.valueOf(2));
    assertThat(mutableBeaconStateElectra.getValidators().size()).isEqualTo(2);
  }

  @Test
  void electraPendingPartialWithdrawals_SingleExceedingMinimumBalance() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 minActivationBalance = specConfigElectra.getMaxEffectiveBalance();
    final UInt64 partialWithdrawalBalance = UInt64.valueOf(10_000L);
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(minActivationBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(0, partialWithdrawalBalance.times(2))
                .build());

    final ExpectedWithdrawals withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    assertThat(withdrawals.getPartialWithdrawalCount()).isEqualTo(1);
    // although the withdrawal was for 2x the available, we could only satisfy half of it
    assertThat(withdrawals.getWithdrawalList().get(0).getAmount())
        .isEqualTo(partialWithdrawalBalance);
  }

  @Test
  void electraPendingPartialWithdrawals_MultipleExceedingMinimumBalance() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 minActivationBalance = specConfigElectra.getMaxEffectiveBalance();
    final UInt64 partialWithdrawalBalance = UInt64.valueOf(10_000L);
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(minActivationBalance.plus(15_000))
                .pendingPartialWithdrawal(0, partialWithdrawalBalance)
                .pendingPartialWithdrawal(0, partialWithdrawalBalance)
                .build());

    final ExpectedWithdrawals withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    assertThat(withdrawals.getPartialWithdrawalCount()).isEqualTo(2);
    // the first withdrawal could be fully satisfied
    assertThat(withdrawals.getWithdrawalList().get(0).getAmount())
        .isEqualTo(partialWithdrawalBalance);
    // the second withdrawal could only be partially satisfied
    assertThat(withdrawals.getWithdrawalList().get(1).getAmount())
        .isEqualTo(UInt64.valueOf(5_000L));
  }

  @Test
  void electraPendingPartialWithdrawals_MultipleExceedingMinimumBalanceOneSkipped() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 minActivationBalance = specConfigElectra.getMaxEffectiveBalance();
    final UInt64 partialWithdrawalBalance = UInt64.valueOf(10_000L);
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(minActivationBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(0, partialWithdrawalBalance)
                // this second pending partial will end up ignored because of insufficient balance
                .pendingPartialWithdrawal(0, partialWithdrawalBalance)
                .build());

    final ExpectedWithdrawals withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    // 2 partials came out of the state queue
    assertThat(withdrawals.getPartialWithdrawalCount()).isEqualTo(2);
    // only 1 withdrawal added because we had insufficient balance to allow the second
    assertThat(withdrawals.getWithdrawalList().size()).isEqualTo(1);
    // the first withdrawal was allowed at the requested amount
    assertThat(withdrawals.getWithdrawalList().get(0).getAmount())
        .isEqualTo(UInt64.valueOf(10_000L));
  }

  @Test
  void electraPendingPartialWithdrawals() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 electraMaxBalance = specConfigElectra.getMaxEffectiveBalance();
    final long partialWithdrawalBalance = 10241024L;

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance))
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance + 1))
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance + 2))
                .pendingPartialWithdrawal(0, electraMaxBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(
                    1, electraMaxBalance.plus(partialWithdrawalBalance).plus(1))
                .pendingPartialWithdrawal(
                    2, electraMaxBalance.plus(partialWithdrawalBalance).plus(2))
                .build());

    final ExpectedWithdrawals withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    final MutableBeaconStateElectra mutableBeaconStateElectra =
        MutableBeaconStateElectra.required(preState.createWritableCopy());
    assertThat(withdrawals.getPartialWithdrawalCount()).isEqualTo(2);

    withdrawals.processWithdrawalsUnchecked(
        mutableBeaconStateElectra,
        SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions()),
        spec.getGenesisSpec().beaconStateMutators(),
        SpecConfigElectra.required(spec.getGenesisSpecConfig()));
    assertThat(mutableBeaconStateElectra.getPendingPartialWithdrawals().size()).isEqualTo(1);
    assertThat(mutableBeaconStateElectra.getNextWithdrawalIndex()).isEqualTo(UInt64.valueOf(2));
    assertThat(mutableBeaconStateElectra.getValidators().size()).isEqualTo(3);
  }

  @Test
  void electraPendingPartialCountsSkippedWithdrawals() {
    spec = TestSpecFactory.createMinimalElectra();
    dataStructureUtil = new DataStructureUtil(spec);
    final SpecConfigElectra specConfigElectra =
        SpecConfigElectra.required(spec.getGenesisSpec().getConfig());
    final UInt64 electraMaxBalance = specConfigElectra.getMaxEffectiveBalance();
    final long partialWithdrawalBalance = 10241024L;

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            new BeaconStateTestBuilder(dataStructureUtil)
                .activeConsolidatingValidator(electraMaxBalance.plus(partialWithdrawalBalance))
                // the two validators below are skipped because they are queued for exit
                .activeConsolidatingValidatorQueuedForExit(
                    electraMaxBalance.plus(partialWithdrawalBalance + 1))
                .activeConsolidatingValidatorQueuedForExit(
                    electraMaxBalance.plus(partialWithdrawalBalance + 2))
                .pendingPartialWithdrawal(0, electraMaxBalance.plus(partialWithdrawalBalance))
                .pendingPartialWithdrawal(
                    1, electraMaxBalance.plus(partialWithdrawalBalance).plus(1))
                .pendingPartialWithdrawal(
                    2, electraMaxBalance.plus(partialWithdrawalBalance).plus(2))
                .build());

    final ExpectedWithdrawals withdrawals =
        spec.getBlockProcessor(preState.getSlot()).getExpectedWithdrawals(preState);
    assertThat(withdrawals.getPartialWithdrawalCount()).isEqualTo(3);
  }
}
