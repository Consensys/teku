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
import static tech.pegasys.artemis.core.BlockProcessorUtil.process_proposer_slashings;

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
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.ssz.SSZTypes.SSZList;

@ExtendWith(BouncyCastleExtension.class)
public class proposer_slashing extends TestSuite {

  @ParameterizedTest(name = "{index}. mainnet process proposer slashing")
  @MethodSource({"mainnetProposerSlashingSetup", "minimalProposerSlashingSetup"})
  void processProposerSlashing(ProposerSlashing proposerSlashing, BeaconState pre) {
    assertThrows(
        BlockProcessingException.class,
        () ->
            pre.updated(
                state -> process_proposer_slashings(state, SSZList.singleton(proposerSlashing))));
  }

  @ParameterizedTest(name = "{index}. mainnet process proposer slashing")
  @MethodSource({"mainnetProposerSlashingSuccessSetup", "minimalProposerSlashingSuccessSetup"})
  void processProposerSlashing(
      ProposerSlashing proposerSlashing, BeaconState pre, BeaconState post) {
    BeaconState wState =
        assertDoesNotThrow(
            () ->
                pre.updated(
                    state ->
                        process_proposer_slashings(state, SSZList.singleton(proposerSlashing))));
    assertEquals(post, wState);
  }

  @MustBeClosed
  static Stream<Arguments> proposerSlashingSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "proposer_slashing", "pyspec_tests");
    return operationSetup(path, Paths.get(config), "proposer_slashing.ssz", ProposerSlashing.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalProposerSlashingSetup() throws Exception {
    return proposerSlashingSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetProposerSlashingSetup() throws Exception {
    return proposerSlashingSetup("mainnet");
  }

  @MustBeClosed
  static Stream<Arguments> proposerSlashingSuccessSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "proposer_slashing", "pyspec_tests");
    return operationSuccessSetup(
        path, Paths.get(config), "proposer_slashing.ssz", ProposerSlashing.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalProposerSlashingSuccessSetup() throws Exception {
    return proposerSlashingSuccessSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetProposerSlashingSuccessSetup() throws Exception {
    return proposerSlashingSuccessSetup("mainnet");
  }
}
