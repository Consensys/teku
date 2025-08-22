/*
 * Copyright Consensys Software Inc., 2025
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
    return getSpec(false);
  }

  public Spec getSpec(final Boolean blsDisabled) {
    if (spec == null) {
      createSpec(blsDisabled);
    }
    return spec;
  }

  private void createSpec(final Boolean blsDisabled) {
    final Eth2Network network =
        switch (configName) {
          case TestSpecConfig.MAINNET -> Eth2Network.MAINNET;
          case TestSpecConfig.MINIMAL -> Eth2Network.MINIMAL;
          default -> throw new IllegalArgumentException("Unknown configName: " + configName);
        };
    final SpecMilestone milestone =
        switch (fork) {
          case TestFork.PHASE0 -> SpecMilestone.PHASE0;
          case TestFork.ALTAIR -> SpecMilestone.ALTAIR;
          case TestFork.BELLATRIX -> SpecMilestone.BELLATRIX;
          case TestFork.CAPELLA -> SpecMilestone.CAPELLA;
          case TestFork.DENEB -> SpecMilestone.DENEB;
          case TestFork.ELECTRA -> SpecMilestone.ELECTRA;
          case TestFork.FULU -> SpecMilestone.FULU;
          default -> throw new IllegalArgumentException("Unknown fork: " + fork);
        };
    spec = TestSpecFactory.create(milestone, network, builder -> builder.blsDisabled(blsDisabled));
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
