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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings("MustBeClosedChecker")
public class PyspecTestFinder implements TestFinder {

  public static final String PYSPEC_TEST_DIRECTORY_NAME = "pyspec_tests";

  private final Map<ForkAndConfig, List<String>> onlyTestsToRunByForkAndConfig = new HashMap<>();
  private final Map<ForkAndConfig, List<String>> testsToIgnoreByForkAndConfig = new HashMap<>();

  /** Used when we want to run ALL pyspec tests. */
  public PyspecTestFinder() {}

  /**
   * Used when we want to limit the spec test for specific tests. This is particularly useful when
   * we are implementing a new fork and can't support all tests yet.
   *
   * @param onlyTestsToRunByForkAndConfig Only tests by {@link ForkAndConfig} (using the format
   *     `{test-type} - {test-name}`) are going to run. The match is a partial match (if the test
   *     starts with the filter value). If empty, all tests would be run.
   * @param testsToIgnoreByForkAndConfig Tests by {@link ForkAndConfig} (using the format
   *     `{test-type} - {test-name}`) that will not be run if there is a match. The match is a
   *     partial match (if test starts with the filter value). If empty, all tests would be run.
   */
  public PyspecTestFinder(
      final Map<ForkAndConfig, List<String>> onlyTestsToRunByForkAndConfig,
      final Map<ForkAndConfig, List<String>> testsToIgnoreByForkAndConfig) {
    this.onlyTestsToRunByForkAndConfig.putAll(onlyTestsToRunByForkAndConfig);
    this.testsToIgnoreByForkAndConfig.putAll(testsToIgnoreByForkAndConfig);
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
    final ForkAndConfig forkAndConfig = new ForkAndConfig(fork, config);
    final List<String> onlyTestsToRun =
        onlyTestsToRunByForkAndConfig.getOrDefault(forkAndConfig, Collections.emptyList());
    final List<String> testsToIgnore =
        testsToIgnoreByForkAndConfig.getOrDefault(forkAndConfig, Collections.emptyList());
    return Files.list(pyspecDir)
        .filter(testDir -> !testDir.getFileName().toString().equals(".DS_Store"))
        .map(
            testDir -> {
              final String testName = pyspecDir.relativize(testDir).toString();
              return new TestDefinition(
                  fork, config, testType, testName, testRoot.relativize(testDir));
            })
        .filter(
            testDefinition ->
                onlyTestsToRun.isEmpty()
                    || onlyTestsToRun.stream()
                        .anyMatch(
                            test ->
                                (testDefinition.getTestType()
                                        + " - "
                                        + testDefinition.getTestName())
                                    .startsWith(test)))
        .filter(
            testDefinition ->
                testsToIgnore.stream()
                    .noneMatch(
                        test ->
                            (testDefinition.getTestType() + " - " + testDefinition.getTestName())
                                .startsWith(test)));
  }

  public record ForkAndConfig(String fork, String config) {}
}
