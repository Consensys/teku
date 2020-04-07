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
import static tech.pegasys.artemis.core.BlockProcessorUtil.process_voluntary_exits;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.core.exceptions.BlockProcessingException;
import tech.pegasys.artemis.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.util.SSZTypes.SSZList;

@ExtendWith(BouncyCastleExtension.class)
public class voluntary_exit extends TestSuite {

  @ParameterizedTest(name = "{index}. process voluntary_exit")
  @MethodSource({"mainnetVoluntaryExitSetup", "minimalVoluntaryExitSetup"})
  void processVoluntaryExit(SignedVoluntaryExit voluntary_exit, BeaconState pre) {
    assertThrows(
        BlockProcessingException.class,
        () ->
            pre.updated(
                state -> process_voluntary_exits(state, SSZList.singleton(voluntary_exit))));
  }

  @ParameterizedTest(name = "{index}. process voluntary_exit")
  @MethodSource({"mainnetVoluntaryExitSuccessSetup", "minimalVoluntaryExitSuccessSetup"})
  void processVoluntaryExit(SignedVoluntaryExit voluntary_exit, BeaconState pre, BeaconState post) {
    BeaconState wState =
        assertDoesNotThrow(
            () ->
                pre.updated(
                    state -> process_voluntary_exits(state, SSZList.singleton(voluntary_exit))));
    assertEquals(post, wState);
  }

  @MustBeClosed
  static Stream<Arguments> voluntary_exitSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "voluntary_exit", "pyspec_tests");
    return operationSetup(path, Paths.get(config), "voluntary_exit.ssz", SignedVoluntaryExit.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalVoluntaryExitSetup() throws Exception {
    return voluntary_exitSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetVoluntaryExitSetup() throws Exception {
    return voluntary_exitSetup("mainnet");
  }

  @MustBeClosed
  static Stream<Arguments> voluntary_exitSuccessSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "voluntary_exit", "pyspec_tests");
    return operationSuccessSetup(
        path, Paths.get(config), "voluntary_exit.ssz", SignedVoluntaryExit.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalVoluntaryExitSuccessSetup() throws Exception {
    return voluntary_exitSuccessSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetVoluntaryExitSuccessSetup() throws Exception {
    return voluntary_exitSuccessSetup("mainnet");
  }
}
