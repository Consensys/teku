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

package tech.pegasys.teku.reference.phase0.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;

import com.google.common.collect.ImmutableMap;
import java.util.function.Supplier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.Deltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;

public class RewardsTestExecutor implements TestExecutor {

  public static final ImmutableMap<String, TestExecutor> REWARDS_TEST_TYPES =
      ImmutableMap.of(
          "rewards/basic", new RewardsTestExecutor(),
          "rewards/leak", new RewardsTestExecutor(),
          "rewards/random", new RewardsTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz_snappy");

    final ValidatorStatusFactory statusFactory =
        testDefinition.getSpec().getGenesisSpec().getValidatorStatusFactory();
    final ValidatorStatuses validatorStatuses = statusFactory.createValidatorStatuses(preState);
    final RewardsAndPenaltiesCalculator calculator =
        testDefinition
            .getSpec()
            .getGenesisSpec()
            .getEpochProcessor()
            .createRewardsAndPenaltiesCalculator(preState, validatorStatuses);
    runTest(testDefinition, calculator);
  }

  private void runTest(
      final TestDefinition testDefinition, final RewardsAndPenaltiesCalculator calculator) {
    assertDeltas(
        testDefinition,
        "head_deltas.ssz_snappy",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applyHeadDelta(
                    validator, baseReward, totalBalances, finalityDelay, delta)));
    assertDeltas(
        testDefinition,
        "inactivity_penalty_deltas.ssz_snappy",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applyInactivityPenaltyDelta(
                    validator, baseReward, finalityDelay, delta)));
    assertDeltas(
        testDefinition,
        "inclusion_delay_deltas.ssz_snappy",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applyInclusionDelayDelta(validator, baseReward, delta, deltas)));
    assertDeltas(
        testDefinition,
        "source_deltas.ssz_snappy",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applySourceDelta(
                    validator, baseReward, totalBalances, finalityDelay, delta)));
    assertDeltas(
        testDefinition,
        "target_deltas.ssz_snappy",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applyTargetDelta(
                    validator, baseReward, totalBalances, finalityDelay, delta)));
  }

  private Supplier<Deltas> apply(
      final RewardsAndPenaltiesCalculator calculator,
      final RewardsAndPenaltiesCalculator.Step step) {
    return () -> calculator.getDeltas(step);
  }

  private void assertDeltas(
      final TestDefinition testDefinition,
      final String expectedResultsFileName,
      final Supplier<Deltas> function) {
    final Deltas expectedDeltas =
        loadSsz(testDefinition, expectedResultsFileName, ExpectedDeltas.SSZ_SCHEMA).getDeltas();
    final Deltas actualDeltas = function.get();
    assertThat(actualDeltas)
        .describedAs(expectedResultsFileName)
        .isEqualToComparingFieldByField(expectedDeltas);
  }
}
