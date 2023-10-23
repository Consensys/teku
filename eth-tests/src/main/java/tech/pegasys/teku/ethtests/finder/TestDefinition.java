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

package tech.pegasys.teku.ethtests.finder;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import tech.pegasys.teku.ethtests.TestFork;
import tech.pegasys.teku.ethtests.TestSpecConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.DenebBuilder;
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
    return getSpec(true);
  }

  public Spec getSpec(final boolean kzgNoop) {
    if (spec == null) {
      createSpec(kzgNoop);
    }
    return spec;
  }

  private void createSpec(final boolean kzgNoop) {
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
          default -> throw new IllegalArgumentException("Unknown fork: " + fork);
        };
    spec =
        TestSpecFactory.create(
            milestone,
            network,
            configBuilder -> {
              if (!kzgNoop) {
                configBuilder.denebBuilder(denebConfigKzgModifier(network));
              }
            });
  }

  private Consumer<DenebBuilder> denebConfigKzgModifier(final Eth2Network network) {
    return builder -> {
      final String trustedSetupFilename =
          network.equals(Eth2Network.MAINNET)
              ? "mainnet-trusted-setup.txt"
              : "minimal-trusted-setup.txt";
      final String trustedSetupPath =
          Objects.requireNonNull(Eth2NetworkConfiguration.class.getResource(trustedSetupFilename))
              .toExternalForm();
      builder.trustedSetupPath(trustedSetupPath).kzgNoop(false);
    };
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
