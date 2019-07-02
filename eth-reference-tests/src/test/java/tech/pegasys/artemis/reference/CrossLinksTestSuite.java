/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.reference;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.state.BeaconState;

public class CrossLinksTestSuite extends BeaconStateTestHelper {
  private static String testFile = "**/tests/epoch_processing/crosslinks/crosslinks_minimal.yaml";

  @ParameterizedTest(name = "crosslinks")
  @MethodSource("testCases")
  void epoch_processing(LinkedHashMap<String, Object> pre, LinkedHashMap<String, Object> post) {
    // State before processing
    BeaconState preState = convertMapToBeaconState(pre);
    // Expected state after processing
    BeaconState postState = convertMapToBeaconState(post);
    // process crosslinks
    // assertTrue(preState.equals(postState));
  }

  @MustBeClosed
  private static Stream<Arguments> testCases() throws IOException {
    return findTests(testFile, "test_cases");
  }
}
