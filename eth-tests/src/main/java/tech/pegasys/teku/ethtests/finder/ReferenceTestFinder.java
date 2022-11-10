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

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import tech.pegasys.teku.ethtests.TestFork;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingFunction;

@SuppressWarnings("MustBeClosedChecker")
public class ReferenceTestFinder {

  private static final Path TEST_PATH_FROM_MODULE =
      Path.of("src", "referenceTest", "resources", "eth2.0-spec-tests", "tests");
  private static final List<String> SUPPORTED_FORKS =
      List.of(TestFork.PHASE0, TestFork.ALTAIR, TestFork.BELLATRIX, TestFork.CAPELLA);

  @MustBeClosed
  public static Stream<TestDefinition> findReferenceTests() throws IOException {
    return findSpecDirectories().flatMap(unchecked(ReferenceTestFinder::findTestTypes));
  }

  @MustBeClosed
  private static Stream<TestDefinition> findTestTypes(final Path specDirectory) throws IOException {
    final String spec = specDirectory.getFileName().toString();
    if (spec.equals("bls")) {
      return new BlsRefTestFinder().findTests(TestFork.PHASE0, spec, specDirectory);
    }
    return SUPPORTED_FORKS.stream()
        .flatMap(
            fork -> {
              final Path testsPath = specDirectory.resolve(fork);
              if (!testsPath.toFile().exists()) {
                return Stream.empty();
              }

              return Stream.of(
                      new BlsTestFinder(),
                      new BlsRefTestFinder(),
                      new SszTestFinder("ssz_generic"),
                      new SszTestFinder("ssz_static"),
                      new ShufflingTestFinder(),
                      new PyspecTestFinder())
                  .flatMap(unchecked(finder -> finder.findTests(fork, spec, testsPath)));
            });
  }

  @MustBeClosed
  private static Stream<Path> findSpecDirectories() throws IOException {
    return Files.list(findReferenceTestRootDirectory());
  }

  public static Path findReferenceTestRootDirectory() {
    final List<Path> searchPaths =
        List.of(
            Path.of(System.getProperty("teku.ref-test-module.path", "")), // Set explicitly
            Path.of(System.getProperty("user.dir")), // Run from eth-reference-tests module
            Path.of(System.getProperty("user.dir"), "eth-reference-tests") // Run from teku root
            );
    return searchPaths.stream()
        .map(path -> path.resolve(TEST_PATH_FROM_MODULE))
        .filter(path -> path.toFile().exists())
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to find the reference tests module. Try setting teku.ref-test-module.path system property and ensure you have run ./gradlew expandRefTests"));
  }

  static <I, O> Function<I, O> unchecked(final ExceptionThrowingFunction<I, O> function) {
    return input -> {
      try {
        return function.apply(input);
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    };
  }
}
