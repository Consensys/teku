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

import static tech.pegasys.teku.ethtests.finder.ReferenceTestFinder.unchecked;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@SuppressWarnings("MustBeClosedChecker")
public class MerkleProofTestFinder implements TestFinder {

  public static final String PROOF_DATA_FILE = "proof.yaml";

  @Override
  @MustBeClosed
  public Stream<TestDefinition> findTests(
      final String fork, final String config, final Path testRoot) throws IOException {
    final Path merkleProofTestDir = testRoot.resolve("merkle_proof");
    if (!merkleProofTestDir.toFile().exists()) {
      return Stream.empty();
    }
    return Files.list(merkleProofTestDir)
        .flatMap(unchecked(dir -> findMerkleProofTests(fork, config, testRoot, dir)));
  }

  @MustBeClosed
  private Stream<TestDefinition> findMerkleProofTests(
      final String fork, final String config, final Path testRoot, final Path testCategoryDir)
      throws IOException {
    final String testType = testRoot.relativize(testCategoryDir).toString();
    return Files.walk(testCategoryDir)
        .filter(path -> path.resolve(PROOF_DATA_FILE).toFile().exists())
        .map(
            testDir -> {
              final String testName = testCategoryDir.relativize(testDir).toString();
              return new TestDefinition(
                  fork, config, testType, testName, testRoot.relativize(testDir));
            });
  }
}
