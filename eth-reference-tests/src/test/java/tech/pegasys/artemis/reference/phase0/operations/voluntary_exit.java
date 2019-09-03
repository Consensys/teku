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

package tech.pegasys.artemis.reference.phase0.operations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_voluntary_exits;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;

@ExtendWith(BouncyCastleExtension.class)
public class voluntary_exit extends TestSuite {

  @ParameterizedTest(name = "{index}. minimal process voluntary_exit: {4}")
  @MethodSource("mainnetVoluntaryExitSetup")
  void mainnetProcessVoluntaryExit(
      VoluntaryExit voluntary_exit,
      BeaconState pre,
      BeaconState post,
      Boolean succesTest,
      String testName)
      throws Exception {
    List<VoluntaryExit> voluntary_exits = new ArrayList<>();
    voluntary_exits.add(voluntary_exit);
    if (succesTest) {
      assertDoesNotThrow(() -> process_voluntary_exits(pre, voluntary_exits));
      assertEquals(pre, post);
    } else {
      assertThrows(
          BlockProcessingException.class, () -> process_voluntary_exits(pre, voluntary_exits));
    }
  }

  @ParameterizedTest(name = "{index}. minimal process voluntary_exit: {4}")
  @MethodSource("minimalVoluntaryExitSetup")
  void minimalProcessVoluntaryExit(
      VoluntaryExit voluntary_exit,
      BeaconState pre,
      BeaconState post,
      Boolean succesTest,
      String testName)
      throws Exception {
    List<VoluntaryExit> voluntary_exits = new ArrayList<>();
    voluntary_exits.add(voluntary_exit);
    if (succesTest) {
      assertDoesNotThrow(() -> process_voluntary_exits(pre, voluntary_exits));
      assertEquals(pre, post);
    } else {
      assertThrows(
          BlockProcessingException.class, () -> process_voluntary_exits(pre, voluntary_exits));
    }
  }

  @MustBeClosed
  static Stream<Arguments> voluntary_exitSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "voluntary_exit", "pyspec_tests");
    return operationSetup(path, Paths.get(config), "voluntary_exit.ssz", VoluntaryExit.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalVoluntaryExitSetup() throws Exception {
    return voluntary_exitSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetVoluntaryExitSetup() throws Exception {
    return voluntary_exitSetup("mainnet");
  }
}
