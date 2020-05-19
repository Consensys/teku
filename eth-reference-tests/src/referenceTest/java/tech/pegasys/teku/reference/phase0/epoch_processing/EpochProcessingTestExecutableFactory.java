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
import org.junit.jupiter.api.function.Executable;
import tech.pegasys.teku.core.EpochProcessorUtil;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconState.Mutator;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.ExecutableFactory;

public class EpochProcessingTestExecutableFactory implements ExecutableFactory {

  public static ImmutableMap<String, ExecutableFactory> EPOCH_PROCESSING_TEST_TYPES =
      ImmutableMap.<String, ExecutableFactory>builder()
          .put(
              "epoch_processing/slashings",
              new EpochProcessingTestExecutableFactory(EpochProcessorUtil::process_slashings))
          .put(
              "epoch_processing/registry_updates",
              new EpochProcessingTestExecutableFactory(
                  EpochProcessorUtil::process_registry_updates))
          .put(
              "epoch_processing/final_updates",
              new EpochProcessingTestExecutableFactory(EpochProcessorUtil::process_final_updates))
          .put(
              "epoch_processing/rewards_and_penalties",
              new EpochProcessingTestExecutableFactory(
                  EpochProcessorUtil::process_rewards_and_penalties))
          .put(
              "epoch_processing/justification_and_finalization",
              new EpochProcessingTestExecutableFactory(
                  EpochProcessorUtil::process_justification_and_finalization))
          .build();

  private final Mutator<? extends Throwable, ? extends Throwable, ? extends Throwable> operation;

  public EpochProcessingTestExecutableFactory(
      final Mutator<? extends Throwable, ? extends Throwable, ? extends Throwable> operation) {
    this.operation = operation;
  }

  @Override
  public Executable forTestDefinition(final TestDefinition testDefinition) {
    return () -> {
      final BeaconState pre = loadStateFromSsz(testDefinition, "pre.ssz");
      final BeaconState expected = loadStateFromSsz(testDefinition, "post.ssz");
      final BeaconState result = pre.updated(operation);
      assertThat(result).isEqualTo(expected);
    };
  }
}
