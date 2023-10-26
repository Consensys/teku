/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.reference.phase0.kzg;

import static tech.pegasys.teku.ethtests.finder.KzgTestFinder.KZG_DATA_FILE;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.ckzg4844.CKZG4844;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;

public abstract class KzgTestExecutor implements TestExecutor {

  private static final Pattern TEST_NAME_PATTERN = Pattern.compile("kzg-(.+)/.+");

  protected final KZG kzg = CKZG4844.getInstance();

  @Override
  public final void runTest(final TestDefinition testDefinition) throws Throwable {
    final String network = extractNetwork(testDefinition.getTestName());
    final Eth2NetworkConfiguration networkConfig =
        Eth2NetworkConfiguration.builder(network).build();
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigDeneb.required(networkConfig.getSpec().getGenesisSpecConfig());

    kzg.loadTrustedSetup(specConfigDeneb.getTrustedSetupPath().orElseThrow());
    runTestImpl(testDefinition);
  }

  private String extractNetwork(final String testName) {
    final Matcher matcher = TEST_NAME_PATTERN.matcher(testName);
    if (matcher.find()) {
      return matcher.group(1);
    }
    throw new IllegalArgumentException("Can't extract network from " + testName);
  }

  protected <T> T loadDataFile(final TestDefinition testDefinition, final Class<T> type)
      throws IOException {
    String dataFile =
        testDefinition.getTestName().endsWith(".yaml")
            ? testDefinition.getTestName()
            : KZG_DATA_FILE;
    return TestDataUtils.loadYaml(testDefinition, dataFile, type);
  }

  protected abstract void runTestImpl(TestDefinition testDefinition) throws Throwable;
}
