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

package tech.pegasys.teku.reference;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ethtests.finder.ReferenceTestFinder;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

/**
 * IntelliJ doesn't seem to like running tests from the generated source directory so this provides
 * a convenient way to run reference tests in IntelliJ.
 *
 * <p>The test case is disabled as the tests run via the generated classes in CI, but it still runs
 * without removing the @Disabled in IntelliJ.
 */
@Disabled
public class ManualReferenceTestRunner extends Eth2ReferenceTestCase {

  /**
   * Filter test to run to those with test type starting with this value.
   *
   * <p>e.g. set to "ssz_static" to run only ssz static tests or "ssz_static/Attestation" for only
   * attestation ssz tests.
   */
  private static final String TEST_TYPE = "sync/optimistic";

  /** Filter test to run to those from the specified spec. One of general, minimal or mainnet */
  private static final String SPEC = "";

  /** Filter test to run only those for a specific milestone. Use values from TestFork. */
  private static final String MILESTONE = null;

  /** Filter tests to run only those where the display name contains this string. */
  private static final String DISPLAY_NAME = "";

  @ParameterizedTest(name = "{0}")
  @MethodSource("loadReferenceTests")
  void shouldRunReferenceTest(final String name, final TestDefinition testDefinition)
      throws Throwable {
    runReferenceTest(testDefinition);
  }

  @SuppressWarnings("ConstantConditions")
  @MustBeClosed
  static Stream<Arguments> loadReferenceTests() throws IOException {
    return ReferenceTestFinder.findReferenceTests()
        .filter(
            testDefinition ->
                SPEC.isBlank() || testDefinition.getConfigName().equalsIgnoreCase(SPEC))
        .filter(testDefinition -> testDefinition.getTestType().startsWith(TEST_TYPE))
        .filter(ManualReferenceTestRunner::isSelectedMilestone)
        .filter(test -> test.getDisplayName().contains(DISPLAY_NAME))
        .map(testDefinition -> Arguments.of(testDefinition.getDisplayName(), testDefinition));
  }

  @SuppressWarnings("ConstantConditions")
  private static boolean isSelectedMilestone(final TestDefinition testDefinition) {
    return MILESTONE == null || MILESTONE.equals(testDefinition.getFork());
  }
}
