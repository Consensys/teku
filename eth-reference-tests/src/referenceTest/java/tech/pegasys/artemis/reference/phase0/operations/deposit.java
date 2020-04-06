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
import static tech.pegasys.artemis.core.BlockProcessorUtil.process_deposits;

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
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.util.SSZTypes.SSZList;

@ExtendWith(BouncyCastleExtension.class)
public class deposit extends TestSuite {

  @ParameterizedTest(name = "{index}. process deposit success")
  @MethodSource({"mainnetDepositSuccessSetup", "minimalDepositSuccessSetup"})
  void processDepositSuccess(Deposit deposit, BeaconState pre, BeaconState post) {
    BeaconState postState =
        assertDoesNotThrow(
            () -> pre.updated(state -> process_deposits(state, SSZList.singleton(deposit))));
    assertEquals(post, postState);
  }

  @ParameterizedTest(name = "{index}. process deposit")
  @MethodSource({"mainnetDepositSetup", "minimalDepositSetup"})
  void processDeposit(Deposit deposit, BeaconState pre) {
    assertThrows(
        BlockProcessingException.class,
        () -> pre.updated(state -> process_deposits(state, SSZList.singleton(deposit))));
  }

  @MustBeClosed
  static Stream<Arguments> depositSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "deposit", "pyspec_tests");
    return operationSetup(path, Paths.get(config), "deposit.ssz", Deposit.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalDepositSetup() throws Exception {
    return depositSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetDepositSetup() throws Exception {
    return depositSetup("mainnet");
  }

  @MustBeClosed
  static Stream<Arguments> depositSuccessSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "deposit", "pyspec_tests");
    return operationSuccessSetup(path, Paths.get(config), "deposit.ssz", Deposit.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalDepositSuccessSetup() throws Exception {
    return depositSuccessSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetDepositSuccessSetup() throws Exception {
    return depositSuccessSetup("mainnet");
  }
}
