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

package tech.pegasys.teku.reference.phase0.epoch_processing;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.core.epoch.EpochProcessorUtil;
import tech.pegasys.teku.core.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconState.Mutator;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class EpochProcessingTestExecutor implements TestExecutor {

  public static ImmutableMap<String, TestExecutor> EPOCH_PROCESSING_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put(
              "epoch_processing/slashings",
              new EpochProcessingTestExecutor(
                  state ->
                      EpochProcessorUtil.process_slashings(
                          state,
                          ValidatorStatuses.create(state).getTotalBalances().getCurrentEpoch())))
          .put(
              "epoch_processing/registry_updates",
              new EpochProcessingTestExecutor(
                  state ->
                      EpochProcessorUtil.process_registry_updates(
                          state, ValidatorStatuses.create(state).getStatuses())))
          .put(
              "epoch_processing/final_updates",
              new EpochProcessingTestExecutor(EpochProcessorUtil::process_final_updates))
          .put(
              "epoch_processing/rewards_and_penalties",
              new EpochProcessingTestExecutor(
                  state ->
                      EpochProcessorUtil.process_rewards_and_penalties(
                          state, ValidatorStatuses.create(state))))
          .put(
              "epoch_processing/justification_and_finalization",
              new EpochProcessingTestExecutor(
                  state ->
                      EpochProcessorUtil.process_justification_and_finalization(
                          state, ValidatorStatuses.create(state).getTotalBalances())))
          .build();

  private final Mutator<? extends Throwable, ? extends Throwable, ? extends Throwable> operation;

  public EpochProcessingTestExecutor(
      final Mutator<? extends Throwable, ? extends Throwable, ? extends Throwable> operation) {
    this.operation = operation;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState pre = loadStateFromSsz(testDefinition, "pre.ssz");
    final BeaconState expected = loadStateFromSsz(testDefinition, "post.ssz");
    final BeaconState result = pre.updated(operation);
    assertThat(result).isEqualTo(expected);
  }
}
