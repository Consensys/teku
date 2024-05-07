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

package tech.pegasys.teku.reference.phase0.kzg;

import static tech.pegasys.teku.ethtests.finder.KzgTestFinder.KZG_DATA_FILE;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.reference.KzgRetriever;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;

public abstract class KzgTestExecutor implements TestExecutor {

  private static final Pattern TEST_NAME_PATTERN = Pattern.compile("kzg-(.+)/.+");
  // TODO: update kzg and retest, should help
  private static final List<String> IGNORED_TESTS =
      List.of(
          "verify_cell_kzg_proof_batch_case_valid_21b209cb4f64d0ed",
          "verify_cell_kzg_proof_batch_case_valid_7dc4b00d04efff0c",
          "verify_cell_kzg_proof_batch_case_valid_fad5448f3ceb0978",
          "verify_cell_kzg_proof_batch_case_valid_unused_row_commitments_bc80af6ef27f8129");

  @Override
  public final void runTest(final TestDefinition testDefinition) throws Throwable {
    final int lastSlash = testDefinition.getTestName().lastIndexOf("/");
    final String testName = testDefinition.getTestName().substring(lastSlash + 1);
    if (IGNORED_TESTS.contains(testName)) {
      return;
    }
    final String network = extractNetwork(testDefinition.getTestName());
    final KZG kzg = KzgRetriever.getKzgWithLoadedTrustedSetup(network);
    runTest(testDefinition, kzg);
  }

  private String extractNetwork(final String testName) {
    final Matcher matcher = TEST_NAME_PATTERN.matcher(testName);
    if (matcher.find()) {
      return matcher.group(1);
    }
    throw new IllegalArgumentException("Can't extract network from " + testName);
  }

  protected <T> T loadDataFile(final TestDefinition testDefinition, final Class<T> type)
      throws IOException {
    String dataFile =
        testDefinition.getTestName().endsWith(".yaml")
            ? testDefinition.getTestName()
            : KZG_DATA_FILE;
    return TestDataUtils.loadYaml(testDefinition, dataFile, type);
  }

  protected abstract void runTest(TestDefinition testDefinition, KZG kzg) throws Throwable;
}
