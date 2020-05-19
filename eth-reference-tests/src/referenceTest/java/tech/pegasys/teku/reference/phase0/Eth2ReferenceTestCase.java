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
import org.junit.jupiter.api.function.Executable;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.bls.BlsTestExecutableFactory;
import tech.pegasys.teku.reference.phase0.epoch_processing.EpochProcessingTestExecutableFactory;
import tech.pegasys.teku.reference.phase0.genesis.GenesisInitializationTestExecutableFactory;
import tech.pegasys.teku.reference.phase0.genesis.GenesisValidityTestExecutableFactory;
import tech.pegasys.teku.reference.phase0.operations.OperationsTestExecutableFactory;
import tech.pegasys.teku.reference.phase0.sanity.SanityBlocksTestExecutableFactory;
import tech.pegasys.teku.reference.phase0.sanity.SanitySlotsTestExecutableFactory;
import tech.pegasys.teku.reference.phase0.shuffling.ShufflingTestExecutableFactory;
import tech.pegasys.teku.reference.phase0.ssz_static.SszTestExecutableFactory;
import tech.pegasys.teku.util.config.Constants;

public abstract class Eth2ReferenceTestCase {

  private final ImmutableMap<String, ExecutableFactory> TEST_TYPES =
      ImmutableMap.<String, ExecutableFactory>builder()
          .putAll(SszTestExecutableFactory.SSZ_TEST_TYPES)
          .putAll(BlsTestExecutableFactory.BLS_TEST_TYPES)
          .putAll(EpochProcessingTestExecutableFactory.EPOCH_PROCESSING_TEST_TYPES)
          .putAll(OperationsTestExecutableFactory.OPERATIONS_TEST_TYPES)
          .put("shuffling", new ShufflingTestExecutableFactory())
          .put("genesis/initialization", new GenesisInitializationTestExecutableFactory())
          .put("genesis/validity", new GenesisValidityTestExecutableFactory())
          .put("sanity/blocks", new SanityBlocksTestExecutableFactory())
          .put("sanity/slots", new SanitySlotsTestExecutableFactory())
          .build();

  protected void runReferenceTest(final TestDefinition testDefinition) throws Throwable {
    if (!testDefinition.getSpec().equals("general")) {
      Constants.setConstants(testDefinition.getSpec());
      if (Constants.SLOTS_PER_HISTORICAL_ROOT
          != SimpleOffsetSerializer.classReflectionInfo
              .get(BeaconStateImpl.class)
              .getVectorLengths()
              .get(0)) {
        SimpleOffsetSerializer.setConstants();
      }
    }
    getExecutableFor(testDefinition).execute();
  }

  private Executable getExecutableFor(final TestDefinition testDefinition) {
    final ExecutableFactory executableFactory = TEST_TYPES.get(testDefinition.getTestType());
    if (executableFactory == null) {
      return () -> Assertions.fail("Unsupported test type " + testDefinition.getTestType());
    }
    return executableFactory.forTestDefinition(testDefinition);
  }
}
