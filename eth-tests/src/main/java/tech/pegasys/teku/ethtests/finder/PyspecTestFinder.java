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

import static tech.pegasys.teku.ethtests.finder.ReferenceTestFinder.unchecked;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("MustBeClosedChecker")
public class PyspecTestFinder implements TestFinder {

  public static final String PYSPEC_TEST_DIRECTORY_NAME = "pyspec_tests";

  private final List<String> onlyTestTypesToRun = new ArrayList<>();
  private final List<String> testTypesToIgnore = new ArrayList<>();

  /** Used when we want to run ALL pyspec tests. */
  public PyspecTestFinder() {}

  /**
   * Used when we want to limit the spec test for specific test types. This is particularly useful
   * when we are implementing a new fork and can't support all test types yet.
   *
   * @param onlyTestTypesToRun Only tests matching these types are going to run. The match is a
   *     partial match (if type starts with the filter value). If empty, all type of tests would be
   *     run.
   * @param testTypesToIgnore Tests matching these types will not be run. The match is a partial
   *     match (if type starts with the filter value). If empty, all type of tests would be run.
   */
  public PyspecTestFinder(
      final List<String> onlyTestTypesToRun, final List<String> testTypesToIgnore) {
    this.onlyTestTypesToRun.addAll(onlyTestTypesToRun);
    this.testTypesToIgnore.addAll(testTypesToIgnore);
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
            testDir -> {
              final String testName = pyspecDir.relativize(testDir).toString();
              return new TestDefinition(
                  fork, config, testType, testName, testRoot.relativize(testDir));
            })
        .filter(
            testDefinition ->
                onlyTestTypesToRun.isEmpty()
                    || onlyTestTypesToRun.stream()
                        .anyMatch(type -> testDefinition.getTestType().startsWith(type)))
        .filter(
            testDefinition ->
                testTypesToIgnore.stream()
                    .noneMatch(type -> testDefinition.getTestType().startsWith(type)));
  }
}
