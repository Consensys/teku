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

import static tech.pegasys.teku.ethtests.finder.ReferenceTestFinder.unchecked;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@SuppressWarnings("MustBeClosedChecker")
public class SszTestFinder implements TestFinder {
  private final String testDirectoryName;

  public SszTestFinder(final String testDirectoryName) {
    this.testDirectoryName = testDirectoryName;
  }

  @Override
  @MustBeClosed
  public Stream<TestDefinition> findTests(final String spec, final Path testRoot)
      throws IOException {
    final Path sszStaticDir = testRoot.resolve(testDirectoryName);
    if (!sszStaticDir.toFile().exists()) {
      return Stream.empty();
    }
    return Files.list(sszStaticDir)
        .flatMap(unchecked(dir -> findSszStaticTests(spec, testRoot, dir)));
  }

  @MustBeClosed
  private static Stream<TestDefinition> findSszStaticTests(
      final String spec, final Path phase0Tests, final Path testCategoryDir) throws IOException {
    final String testType = phase0Tests.relativize(testCategoryDir).toString();
    return Files.walk(testCategoryDir)
        .filter(path -> path.resolve("serialized.ssz_snappy").toFile().exists())
        .map(
            testDir -> {
              final String testName = testCategoryDir.relativize(testDir).toString();
              return new TestDefinition(spec, testType, testName, phase0Tests.relativize(testDir));
            });
  }
}
