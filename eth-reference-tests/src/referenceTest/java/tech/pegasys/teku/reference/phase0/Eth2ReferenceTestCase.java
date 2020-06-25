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

package tech.pegasys.teku.reference.phase0;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.bls.BlsTests;
import tech.pegasys.teku.reference.phase0.epoch_processing.EpochProcessingTestExecutor;
import tech.pegasys.teku.reference.phase0.genesis.GenesisTests;
import tech.pegasys.teku.reference.phase0.operations.OperationsTestExecutor;
import tech.pegasys.teku.reference.phase0.rewards.RewardsTestExecutor;
import tech.pegasys.teku.reference.phase0.sanity.SanityTests;
import tech.pegasys.teku.reference.phase0.shuffling.ShufflingTestExecutor;
import tech.pegasys.teku.reference.phase0.ssz_static.SszTestExecutor;
import tech.pegasys.teku.util.config.Constants;

public abstract class Eth2ReferenceTestCase {

  private final ImmutableMap<String, TestExecutor> TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(SszTestExecutor.SSZ_TEST_TYPES)
          .putAll(BlsTests.BLS_TEST_TYPES)
          .putAll(EpochProcessingTestExecutor.EPOCH_PROCESSING_TEST_TYPES)
          .putAll(OperationsTestExecutor.OPERATIONS_TEST_TYPES)
          .putAll(ShufflingTestExecutor.SHUFFLING_TEST_TYPES)
          .putAll(GenesisTests.GENESIS_TEST_TYPES)
          .putAll(SanityTests.SANITY_TEST_TYPES)
          .putAll(RewardsTestExecutor.REWARDS_TEST_TYPES)
          .build();

  protected void runReferenceTest(final TestDefinition testDefinition) throws Throwable {
    setConstants(testDefinition.getSpec());
    getExecutorFor(testDefinition).runTest(testDefinition);
  }

  private void setConstants(final String spec) {
    if (!spec.equals("general")) {
      Constants.setConstants(spec);
      if (Constants.SLOTS_PER_HISTORICAL_ROOT
          != SimpleOffsetSerializer.classReflectionInfo
              .get(BeaconStateImpl.class)
              .getVectorLengths()
              .get(0)) {
        SimpleOffsetSerializer.setConstants();
      }
    }
  }

  private TestExecutor getExecutorFor(final TestDefinition testDefinition) {
    final TestExecutor testExecutor = TEST_TYPES.get(testDefinition.getTestType());
    if (testExecutor == null) {
      return Assertions.fail("Unsupported test type " + testDefinition.getTestType());
    }
    return testExecutor;
  }
}
