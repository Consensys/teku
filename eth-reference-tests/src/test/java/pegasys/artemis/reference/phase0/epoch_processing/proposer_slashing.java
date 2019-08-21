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

package pegasys.artemis.reference.phase0.epoch_processing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_proposer_slashings;

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
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;

@ExtendWith(BouncyCastleExtension.class)
public class proposer_slashing extends TestSuite {
  @ParameterizedTest(name = "{index}. process ProposerSlashing proposerSlashing={0} -> pre={1} ")
  @MethodSource({
    "processProposerSlashingEpochsAreDifferentSetup",
    "processProposerSlashingHeadersAreSameSetup",
    "processProposerSlashingInvalidProposerIndexSetup",
    "processProposerSlashingProposerIsNotActivatedSetup",
    "processProposerSlashingProposerIsSlashedSetup",
    "processProposerSlashingProposerIsWithdrawnSetup"
  })
  void processProposerSlashing(ProposerSlashing proposerSlashing, BeaconState pre) {
    List<ProposerSlashing> proposerSlashings = new ArrayList<ProposerSlashing>();
    proposerSlashings.add(proposerSlashing);
    assertThrows(
        BlockProcessingException.class,
        () -> {
          process_proposer_slashings(pre, proposerSlashings);
        });
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingEpochsAreDifferentSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get("/mainnet/phase0/operations/proposer_slashing/pyspec_tests/epochs_are_different");
    return operationProposerSlashingType1Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingHeadersAreSameSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get("/mainnet/phase0/operations/proposer_slashing/pyspec_tests/headers_are_same");
    return operationProposerSlashingType1Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingInvalidProposerIndexSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get(
            "/mainnet/phase0/operations/proposer_slashing/pyspec_tests/invalid_proposer_index");
    return operationProposerSlashingType1Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingProposerIsNotActivatedSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get(
            "/mainnet/phase0/operations/proposer_slashing/pyspec_tests/proposer_is_not_activated");
    return operationProposerSlashingType1Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingProposerIsSlashedSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get("/mainnet/phase0/operations/proposer_slashing/pyspec_tests/proposer_is_slashed");
    return operationProposerSlashingType1Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingProposerIsWithdrawnSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get(
            "/mainnet/phase0/operations/proposer_slashing/pyspec_tests/proposer_is_withdrawn");
    return operationProposerSlashingType1Setup(path, configPath);
  }

  @ParameterizedTest(
      name = "{index}. process ProposerSlashing proposerSlashing={0} bls_setting{1} -> pre={2} ")
  @MethodSource({
    "processProposerSlashingInvalidSig1Setup",
    "processProposerSlashingInvalidSig1And2Setup",
    "processProposerSlashingInvalidSig2Setup"
  })
  void processProposerSlashing(
      ProposerSlashing proposerSlashing, Integer bls_setting, BeaconState pre) {
    List<ProposerSlashing> proposerSlashings = new ArrayList<ProposerSlashing>();
    proposerSlashings.add(proposerSlashing);
    assertThrows(
        BlockProcessingException.class,
        () -> {
          process_proposer_slashings(pre, proposerSlashings);
        });
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingInvalidSig1Setup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get("/mainnet/phase0/operations/proposer_slashing/pyspec_tests/invalid_sig_1");
    return operationProposerSlashingType2Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingInvalidSig1And2Setup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get("/mainnet/phase0/operations/proposer_slashing/pyspec_tests/invalid_sig_1_and_2");
    return operationProposerSlashingType2Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingInvalidSig2Setup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get("/mainnet/phase0/operations/proposer_slashing/pyspec_tests/invalid_sig_2");
    return operationProposerSlashingType2Setup(path, configPath);
  }

  @ParameterizedTest(
      name = "{index}. process ProposerSlashing proposerSlashing={0} pre={1} -> post={2} ")
  @MethodSource({"processProposerSlashingSuccessSetup"})
  void processProposerSlashing(
      ProposerSlashing proposerSlashing, BeaconState pre, BeaconState post) {
    List<ProposerSlashing> proposerSlashings = new ArrayList<ProposerSlashing>();
    proposerSlashings.add(proposerSlashing);
    assertDoesNotThrow(
        () -> {
          process_proposer_slashings(pre, proposerSlashings);
        });
    assertEquals(pre, post);
  }

  @MustBeClosed
  static Stream<Arguments> processProposerSlashingSuccessSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/proposer_slashing/pyspec_tests/success");
    return operationProposerSlashingType3Setup(path, configPath);
  }
}
