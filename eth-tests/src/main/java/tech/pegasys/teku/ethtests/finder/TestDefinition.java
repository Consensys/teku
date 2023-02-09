/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethtests.finder;

import java.nio.file.Path;
import tech.pegasys.teku.ethtests.TestFork;
import tech.pegasys.teku.ethtests.TestSpecConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.ProgressiveBalancesMode;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class TestDefinition {

  private final String fork;
  private final String configName;
  private final String testType;
  private final String testName;
  private final Path pathFromPhaseTestDir;
  private Spec spec;

  public TestDefinition(
      final String fork,
      final String configName,
      final String testType,
      final String testName,
      final Path pathFromPhaseTestDir) {
    this.configName = configName;
    this.fork = fork;
    this.testType = testType.replace("\\", "/");
    this.testName = testName.replace("\\", "/");
    this.pathFromPhaseTestDir = pathFromPhaseTestDir;
  }

  public String getConfigName() {
    return configName;
  }

  public String getFork() {
    return fork;
  }

  public Spec getSpec() {
    if (spec == null) {
      createSpec();
    }

    return spec;
  }

  private void createSpec() {
    final Eth2Network network;
    final SpecMilestone highestSupportedMilestone;
    switch (configName) {
      case TestSpecConfig.MAINNET:
        network = Eth2Network.MAINNET;
        break;
      case TestSpecConfig.MINIMAL:
        network = Eth2Network.MINIMAL;
        break;
      default:
        throw new IllegalArgumentException("Unknown configName: " + configName);
    }
    switch (fork) {
      case TestFork.PHASE0:
        highestSupportedMilestone = SpecMilestone.PHASE0;
        break;
      case TestFork.ALTAIR:
        highestSupportedMilestone = SpecMilestone.ALTAIR;
        break;
      case TestFork.BELLATRIX:
        highestSupportedMilestone = SpecMilestone.BELLATRIX;
        break;
      case TestFork.CAPELLA:
        highestSupportedMilestone = SpecMilestone.CAPELLA;
        break;
      case TestFork.DENEB:
        highestSupportedMilestone = SpecMilestone.DENEB;
        break;
      default:
        throw new IllegalArgumentException("Unknown fork: " + fork);
    }
    spec =
        TestSpecFactory.create(
            highestSupportedMilestone,
            network,
            builder -> builder.progressiveBalancesMode(ProgressiveBalancesMode.CHECKED));
  }

  public String getTestType() {
    return testType;
  }

  public String getTestName() {
    return testName;
  }

  @Override
  public String toString() {
    return fork + " - " + configName + " - " + testType + " - " + testName;
  }

  public String getDisplayName() {
    return toString();
  }

  public Path getPathFromPhaseTestDir() {
    return pathFromPhaseTestDir;
  }

  public Path getTestDirectory() {
    return ReferenceTestFinder.findReferenceTestRootDirectory()
        .resolve(Path.of(configName, fork))
        .resolve(pathFromPhaseTestDir);
  }
}
