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
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.core.Deltas;
import tech.pegasys.teku.core.epoch.RewardsAndPenaltiesCalculator;
import tech.pegasys.teku.core.epoch.RewardsAndPenaltiesCalculator.Step;
import tech.pegasys.teku.core.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class RewardsTestExecutor implements TestExecutor {

  public static final ImmutableMap<String, TestExecutor> REWARDS_TEST_TYPES =
      ImmutableMap.of(
          "rewards/basic", new RewardsTestExecutor(),
          "rewards/leak", new RewardsTestExecutor(),
          "rewards/random", new RewardsTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final BeaconState state = loadStateFromSsz(testDefinition, "pre.ssz");
    final ValidatorStatuses validatorStatuses = ValidatorStatuses.create(state);
    final RewardsAndPenaltiesCalculator calculator =
        new RewardsAndPenaltiesCalculator(state, validatorStatuses);
    assertDeltas(
        testDefinition,
        "head_deltas.yaml",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applyHeadDelta(
                    validator, baseReward, totalBalances, finalityDelay, delta)));
    assertDeltas(
        testDefinition,
        "inactivity_penalty_deltas.yaml",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applyInactivityPenaltyDelta(
                    validator, baseReward, finalityDelay, delta)));
    assertDeltas(
        testDefinition,
        "inclusion_delay_deltas.yaml",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applyInclusionDelayDelta(validator, baseReward, delta, deltas)));
    assertDeltas(
        testDefinition,
        "source_deltas.yaml",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applySourceDelta(
                    validator, baseReward, totalBalances, finalityDelay, delta)));
    assertDeltas(
        testDefinition,
        "target_deltas.yaml",
        apply(
            calculator,
            (deltas, totalBalances, finalityDelay, validator, baseReward, delta) ->
                calculator.applyTargetDelta(
                    validator, baseReward, totalBalances, finalityDelay, delta)));
  }

  private Supplier<Deltas> apply(final RewardsAndPenaltiesCalculator calculator, final Step step) {
    return () -> calculator.getDeltas(step);
  }

  private void assertDeltas(
      final TestDefinition testDefinition,
      final String expectedResultsFileName,
      final Supplier<Deltas> function)
      throws IOException {
    final Deltas expectedDeltas =
        loadYaml(testDefinition, expectedResultsFileName, DeltaYaml.class).getDeltas();
    final Deltas actualDeltas = function.get();
    assertThat(actualDeltas)
        .describedAs(expectedResultsFileName)
        .isEqualToComparingFieldByField(expectedDeltas);
  }

  private static class DeltaYaml {

    @JsonProperty(value = "rewards", required = true)
    private List<Long> rewards;

    @JsonProperty(value = "penalties", required = true)
    private List<Long> penalties;

    public Deltas getDeltas() {
      final Deltas deltas = new Deltas(rewards.size());
      for (int i = 0; i < rewards.size(); i++) {
        deltas.getDelta(i).reward(UInt64.fromLongBits(rewards.get(i)));
        deltas.getDelta(i).penalize(UInt64.fromLongBits(penalties.get(i)));
      }
      return deltas;
    }
  }
}
