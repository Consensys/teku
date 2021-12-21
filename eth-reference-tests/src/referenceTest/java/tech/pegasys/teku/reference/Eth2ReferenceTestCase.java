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

package tech.pegasys.teku.reference;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import tech.pegasys.teku.ethtests.TestFork;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.altair.fork.ForkUpgradeTestExecutor;
import tech.pegasys.teku.reference.altair.fork.TransitionTestExecutor;
import tech.pegasys.teku.reference.altair.rewards.RewardsTestExecutorAltair;
import tech.pegasys.teku.reference.altair.rewards.RewardsTestExecutorMerge;
import tech.pegasys.teku.reference.common.epoch_processing.EpochProcessingTestExecutor;
import tech.pegasys.teku.reference.common.operations.OperationsTestExecutor;
import tech.pegasys.teku.reference.phase0.bls.BlsTests;
import tech.pegasys.teku.reference.phase0.forkchoice.ForkChoiceTestExecutor;
import tech.pegasys.teku.reference.phase0.genesis.GenesisTests;
import tech.pegasys.teku.reference.phase0.rewards.RewardsTestExecutorPhase0;
import tech.pegasys.teku.reference.phase0.sanity.SanityTests;
import tech.pegasys.teku.reference.phase0.shuffling.ShufflingTestExecutor;
import tech.pegasys.teku.reference.phase0.ssz_generic.SszGenericTests;
import tech.pegasys.teku.reference.phase0.ssz_static.SszTestExecutor;
import tech.pegasys.teku.reference.phase0.ssz_static.SszTestExecutorDeprecated;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.SpecDependent;

public abstract class Eth2ReferenceTestCase {

  private final ImmutableMap<String, TestExecutor> COMMON_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(BlsTests.BLS_TEST_TYPES)
          .putAll(ForkChoiceTestExecutor.FORK_CHOICE_TEST_TYPES)
          .putAll(GenesisTests.GENESIS_TEST_TYPES)
          .putAll(ShufflingTestExecutor.SHUFFLING_TEST_TYPES)
          .putAll(EpochProcessingTestExecutor.EPOCH_PROCESSING_TEST_TYPES)
          .putAll(SszTestExecutor.SSZ_TEST_TYPES)
          .putAll(SszGenericTests.SSZ_GENERIC_TEST_TYPES)
          .putAll(OperationsTestExecutor.OPERATIONS_TEST_TYPES)
          .putAll(SanityTests.SANITY_TEST_TYPES)
          .putAll(SszTestExecutorDeprecated.SSZ_TEST_TYPES)
          .put("merkle/single_proof", TestExecutor.IGNORE_TESTS)
          .build();

  private final ImmutableMap<String, TestExecutor> PHASE_0_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(RewardsTestExecutorPhase0.REWARDS_TEST_TYPES)
          .build();

  private final ImmutableMap<String, TestExecutor> ALTAIR_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorAltair.REWARDS_TEST_TYPES)
          .build();

  private final ImmutableMap<String, TestExecutor> MERGE_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorMerge.REWARDS_TEST_TYPES)
          .build();

  protected void runReferenceTest(final TestDefinition testDefinition) throws Throwable {
    setConstants(testDefinition.getConfigName());
    getExecutorFor(testDefinition).runTest(testDefinition);
  }

  private void setConstants(final String spec) {
    if (!spec.equals("general") && !spec.equals("bls")) {
      Constants.setConstants(spec);
      SpecDependent.resetAll();
    }
  }

  private TestExecutor getExecutorFor(final TestDefinition testDefinition) {
    TestExecutor testExecutor = null;

    // Look for fork-specific tests first
    if (testDefinition.getFork().equals(TestFork.PHASE0)) {
      testExecutor = PHASE_0_TEST_TYPES.get(testDefinition.getTestType());
    } else if (testDefinition.getFork().equals(TestFork.ALTAIR)) {
      testExecutor = ALTAIR_TEST_TYPES.get(testDefinition.getTestType());
    } else if (testDefinition.getFork().equals(TestFork.MERGE)) {
      testExecutor = MERGE_TEST_TYPES.get(testDefinition.getTestType());
    }

    // Look for a common test type if no specific override present
    if (testExecutor == null) {
      testExecutor = COMMON_TEST_TYPES.get(testDefinition.getTestType());
    }

    if (testExecutor == null) {
      return Assertions.fail("Unsupported test type " + testDefinition.getTestType());
    }
    return testExecutor;
  }
}
