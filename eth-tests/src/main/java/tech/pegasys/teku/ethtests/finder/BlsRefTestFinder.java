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
public class BlsRefTestFinder implements TestFinder {

  @Override
  @MustBeClosed
  public Stream<TestDefinition> findTests(final String fork, final String spec, final Path testRoot)
      throws IOException {
    if (!spec.equals("bls")) {
      return Stream.empty();
    }
    return Files.list(testRoot)
        .filter(path -> path.toFile().isDirectory())
        .flatMap(unchecked(path -> findBlsTests(spec, testRoot, path)));
  }

  @MustBeClosed
  private Stream<TestDefinition> findBlsTests(
      final String spec, final Path testRoot, final Path testCategoryDir) throws IOException {
    final String testType = "bls/" + testRoot.relativize(testCategoryDir).toString();
    return Files.list(testCategoryDir)
        .filter(file -> file.toFile().getName().endsWith(".yaml"))
        .map(
            testFile ->
                new TestDefinition(
                    "",
                    spec,
                    testType,
                    testFile.toFile().getName(),
                    testRoot.relativize(testCategoryDir)));
  }
}
