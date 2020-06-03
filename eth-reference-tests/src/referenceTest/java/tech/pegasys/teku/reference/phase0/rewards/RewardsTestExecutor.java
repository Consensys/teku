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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tech.pegasys.teku.core.Deltas;
import tech.pegasys.teku.core.EpochProcessorUtil;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class RewardsTestExecutor implements TestExecutor {

  public static final Map<String, TestExecutor> REWARDS_TEST_TYPES =
      ImmutableMap.of("rewards/core", new RewardsTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final BeaconState state = loadStateFromSsz(testDefinition, "pre.ssz");
    assertDeltas(testDefinition, "head_deltas.yaml", state, EpochProcessorUtil::getHeadDeltas);
    assertDeltas(
        testDefinition,
        "inactivity_penalty_deltas.yaml",
        state,
        EpochProcessorUtil::getInactivityPenaltyDeltas);
    assertDeltas(
        testDefinition,
        "inclusion_delay_deltas.yaml",
        state,
        EpochProcessorUtil::getInclusionDelayDeltas);
    assertDeltas(testDefinition, "source_deltas.yaml", state, EpochProcessorUtil::getSourceDeltas);
    assertDeltas(testDefinition, "target_deltas.yaml", state, EpochProcessorUtil::getTargetDeltas);
  }

  private void assertDeltas(
      final TestDefinition testDefinition,
      final String expectedResultsFileName,
      final BeaconState state,
      final Function<BeaconState, Deltas> function)
      throws IOException {
    final Deltas expectedDeltas =
        loadYaml(testDefinition, expectedResultsFileName, DeltaYaml.class).getDeltas();
    final Deltas actualDeltas = function.apply(state);
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
      return new Deltas(
          rewards.stream().map(UnsignedLong::fromLongBits).collect(toList()),
          penalties.stream().map(UnsignedLong::fromLongBits).collect(toList()));
    }
  }
}
