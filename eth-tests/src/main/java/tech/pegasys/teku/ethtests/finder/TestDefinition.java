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

package tech.pegasys.teku.ethtests.finder;

import java.nio.file.Path;
import tech.pegasys.teku.networks.SpecProviderFactory;
import tech.pegasys.teku.spec.SpecProvider;

public class TestDefinition {
  private final String specName;
  private final String testType;
  private final String testName;
  private final Path pathFromPhaseTestDir;
  private SpecProvider specProvider;

  public TestDefinition(
      final String specName,
      final String testType,
      final String testName,
      final Path pathFromPhaseTestDir) {
    this.specName = specName;
    this.testType = testType.replace("\\", "/");
    this.testName = testName.replace("\\", "/");
    this.pathFromPhaseTestDir = pathFromPhaseTestDir;
  }

  public String getSpecName() {
    return specName;
  }

  public SpecProvider getSpecProvider() {
    if (specProvider == null) {
      specProvider = SpecProviderFactory.create(specName);
    }
    return specProvider;
  }

  public String getTestType() {
    return testType;
  }

  public String getTestName() {
    return testName;
  }

  @Override
  public String toString() {
    return specName + " - " + testType + " - " + testName;
  }

  public String getDisplayName() {
    return toString();
  }

  public Path getPathFromPhaseTestDir() {
    return pathFromPhaseTestDir;
  }

  public Path getTestDirectory() {
    return ReferenceTestFinder.findReferenceTestRootDirectory()
        .resolve(Path.of(specName, ReferenceTestFinder.PHASE_TEST_DIR))
        .resolve(pathFromPhaseTestDir);
  }
}
