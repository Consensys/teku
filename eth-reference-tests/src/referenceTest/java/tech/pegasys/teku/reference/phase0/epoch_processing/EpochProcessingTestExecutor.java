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
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class EpochProcessingTestExecutor implements TestExecutor {

  private enum Operation {
    PROCESS_SLASHINGS,
    PROCESS_REGISTRY_UPDATES,
    PROCESS_FINAL_UPDATES,
    PROCESS_REWARDS_AND_PENALTIES,
    PROCESS_JUSTIFICATION_AND_FINALIZATION
  };

  public static ImmutableMap<String, TestExecutor> EPOCH_PROCESSING_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put(
              "epoch_processing/slashings",
              new EpochProcessingTestExecutor(Operation.PROCESS_SLASHINGS))
          .put(
              "epoch_processing/registry_updates",
              new EpochProcessingTestExecutor(Operation.PROCESS_REGISTRY_UPDATES))
          .put(
              "epoch_processing/final_updates",
              new EpochProcessingTestExecutor(Operation.PROCESS_FINAL_UPDATES))
          .put(
              "epoch_processing/rewards_and_penalties",
              new EpochProcessingTestExecutor(Operation.PROCESS_REWARDS_AND_PENALTIES))
          .put(
              "epoch_processing/justification_and_finalization",
              new EpochProcessingTestExecutor(Operation.PROCESS_JUSTIFICATION_AND_FINALIZATION))
          .build();

  private final Operation operation;

  public EpochProcessingTestExecutor(final Operation operation) {
    this.operation = operation;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState pre = loadStateFromSsz(testDefinition, "pre.ssz");
    final BeaconState expected = loadStateFromSsz(testDefinition, "post.ssz");
    final BeaconState result = pre.updated(this::executeOperation);
    assertThat(result).isEqualTo(expected);
  }

  private void executeOperation(MutableBeaconState preState) throws EpochProcessingException {
    switch (operation) {
      case PROCESS_SLASHINGS:
        processSlashings(preState);
        break;
      case PROCESS_REGISTRY_UPDATES:
        processRegistryUpdates(preState);
        break;
      case PROCESS_FINAL_UPDATES:
        processFinalUpdates(preState);
        break;
      case PROCESS_REWARDS_AND_PENALTIES:
        processRewardsAndPenalties(preState);
        break;
      case PROCESS_JUSTIFICATION_AND_FINALIZATION:
        processJustificationAndFinalization(preState);
        break;
      default:
        throw new UnsupportedOperationException(
            "Attempted to execute unknown operation type: " + operation);
    }
  }

  private void processSlashings(final MutableBeaconState state) {
    EpochProcessorUtil.process_slashings(
        state, ValidatorStatuses.create(state).getTotalBalances().getCurrentEpoch());
  }

  private void processRegistryUpdates(final MutableBeaconState state)
      throws EpochProcessingException {
    EpochProcessorUtil.process_registry_updates(
        state, ValidatorStatuses.create(state).getStatuses());
  }

  private void processFinalUpdates(final MutableBeaconState state) {
    EpochProcessorUtil.process_final_updates(state);
  }

  private void processRewardsAndPenalties(final MutableBeaconState state)
      throws EpochProcessingException {
    EpochProcessorUtil.process_rewards_and_penalties(state, ValidatorStatuses.create(state));
  }

  private void processJustificationAndFinalization(final MutableBeaconState state)
      throws EpochProcessingException {
    EpochProcessorUtil.process_justification_and_finalization(
        state, ValidatorStatuses.create(state).getTotalBalances());
  }
}
