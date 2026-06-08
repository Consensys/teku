/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.ethtests.finder.ReferenceTestFinder.unchecked;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.spec.config.YamlConfigReader;

@SuppressWarnings("MustBeClosedChecker")
public class PyspecTestFinder implements TestFinder {

  private final YamlConfigReader yamlConfigReader = new YamlConfigReader();

  public static final String PYSPEC_TEST_DIRECTORY_NAME = "pyspec_tests";

  private final List<String> onlyTestsToRun = new ArrayList<>();
  private final List<String> testsToIgnore = new ArrayList<>();

  /** Used when we want to run ALL pyspec tests. */
  public PyspecTestFinder() {}

  /**
   * Allows restricting execution to a specific subset of spec tests. This is especially useful when
   * introducing a new fork and full test coverage is not yet supported.
   *
   * @param onlyTestsToRun Tests that should be executed. Only tests whose identifiers match these
   *     filters—fully or partially—in the format `{fork} - {config} - {test-type} - {test-name}`
   *     will run. If this list is empty, all tests are eligible to run.
   * @param testsToIgnore Tests that should be skipped. Any test whose identifier matches these
   *     filters—fully or partially—in the same format will not run. If this list is empty, no tests
   *     are excluded.
   */
  public PyspecTestFinder(final List<String> onlyTestsToRun, final List<String> testsToIgnore) {
    this.onlyTestsToRun.addAll(onlyTestsToRun);
    this.testsToIgnore.addAll(testsToIgnore);
  }

  @Override
  @MustBeClosed
  public Stream<TestDefinition> findTests(
      final String fork, final String config, final Path testRoot) throws IOException {
    return Files.walk(testRoot)
        .filter(path -> path.resolve(PYSPEC_TEST_DIRECTORY_NAME).toFile().exists())
        .flatMap(
            unchecked(
                testCategoryDir -> findPyspecTestCases(fork, config, testRoot, testCategoryDir)));
  }

  @MustBeClosed
  private Stream<TestDefinition> findPyspecTestCases(
      final String fork, final String config, final Path testRoot, final Path testCategoryDir)
      throws IOException {
    final String testType = testRoot.relativize(testCategoryDir).toString();
    final Path pyspecDir = testCategoryDir.resolve(PYSPEC_TEST_DIRECTORY_NAME);
    return Files.list(pyspecDir)
        .filter(testDir -> !testDir.getFileName().toString().equals(".DS_Store"))
        .map(
            unchecked(
                testDir -> {
                  final String testName = pyspecDir.relativize(testDir).toString();
                  // some reference tests ship a partial config.yaml overriding a handful of
                  // constants on top of the builtin config
                  final Path configOverridesYaml = testDir.resolve("config.yaml");
                  return new TestDefinition(
                      fork,
                      config,
                      testType,
                      testName,
                      testRoot.relativize(testDir),
                      Files.exists(configOverridesYaml)
                          ? Optional.of(readConfigOverrides(configOverridesYaml))
                          : Optional.empty());
                }))
        .filter(
            testDefinition ->
                onlyTestsToRun.isEmpty()
                    || onlyTestsToRun.stream()
                        .anyMatch(
                            testFilter -> testDefinition.getDisplayName().contains(testFilter)))
        .filter(
            testDefinition ->
                testsToIgnore.stream()
                    .noneMatch(testFilter -> testDefinition.getDisplayName().contains(testFilter)));
  }

  private Map<String, Object> readConfigOverrides(final Path configOverridesYaml)
      throws IOException {
    try (final InputStream in = Files.newInputStream(configOverridesYaml)) {
      return yamlConfigReader.readValues(in);
    }
  }
}
