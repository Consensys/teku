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
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.spec.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.statetransition.exceptions.EpochProcessingException;

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
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz");
    final BeaconState expectedPostState = loadStateFromSsz(testDefinition, "post.ssz");
    runStandardTest(testDefinition, preState, expectedPostState);
    runDeprecatedTest(preState, expectedPostState);
  }

  private void runStandardTest(
      final TestDefinition testDefinition,
      final BeaconState preState,
      final BeaconState expectedPostState)
      throws EpochProcessingException {
    final EpochProcessor epochProcessor =
        testDefinition.getSpecProvider().getGenesisSpec().getEpochProcessor();
    final EpochProcessingExecutor processor = new DefaultEpochProcessingExecutor(epochProcessor);
    final BeaconState result = preState.updated(state -> executeOperation(processor, state));
    assertThat(result).isEqualTo(expectedPostState);
  }

  private void runDeprecatedTest(BeaconState preState, BeaconState expectedPostState)
      throws EpochProcessingException {
    final EpochProcessingExecutor processor = new DeprecatedEpochProcessingExecutor();
    final BeaconState result = preState.updated(state -> executeOperation(processor, state));
    assertThat(result).isEqualTo(expectedPostState);
  }

  private void executeOperation(EpochProcessingExecutor processor, MutableBeaconState preState)
      throws EpochProcessingException {
    switch (operation) {
      case PROCESS_SLASHINGS:
        processor.processSlashings(preState);
        break;
      case PROCESS_REGISTRY_UPDATES:
        processor.processRegistryUpdates(preState);
        break;
      case PROCESS_FINAL_UPDATES:
        processor.processFinalUpdates(preState);
        break;
      case PROCESS_REWARDS_AND_PENALTIES:
        processor.processRewardsAndPenalties(preState);
        break;
      case PROCESS_JUSTIFICATION_AND_FINALIZATION:
        processor.processJustificationAndFinalization(preState);
        break;
      default:
        throw new UnsupportedOperationException(
            "Attempted to execute unknown operation type: " + operation);
    }
  }
}
