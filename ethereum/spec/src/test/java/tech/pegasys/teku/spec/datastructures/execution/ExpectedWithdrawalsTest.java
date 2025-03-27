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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
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
