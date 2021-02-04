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

import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.networks.ConstantsLoader;
import tech.pegasys.teku.spec.SpecConfiguration;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.constants.SpecConstants;

public interface TestExecutor {
  TestExecutor IGNORE_TESTS =
      testDefinition -> {
        throw new TestAbortedException(
            "Test " + testDefinition.getDisplayName() + " has been ignored");
      };

  default SpecProvider specProviderFromSpec(final TestDefinition testDefinition) {
    final SpecConstants specConstants = ConstantsLoader.loadConstants(testDefinition.getSpec());
    final SpecConfiguration specConfiguration =
        SpecConfiguration.builder().constants(specConstants).build();
    return SpecProvider.create(specConfiguration);
  }

  void runTest(TestDefinition testDefinition) throws Throwable;
}
