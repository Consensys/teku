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

package pegasys.artemis.reference.mainnet.phase0;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.statetransition.util.BlockProcessorUtil;

@ExtendWith(BouncyCastleExtension.class)
class operations_processing extends TestSuite {
  private static final Path configPath = Paths.get("mainnet", "phase0");

  @ParameterizedTest(name = "{index}. process attestation pre={0} -> post={1}. {arguments}")
  @MethodSource("attestationSetup")
  void processAttestation(
      operations_processing.Context state, operations_processing.Context attestation)
      throws Exception {
    System.out.println("c.path:" + state.path);
    BlockProcessorUtil.process_attestations(
        (BeaconState) state.obj, List.of((Attestation) attestation.obj));
    //    assertEquals(pre, plus);
  }

  @MustBeClosed
  static Stream<Arguments> attestationSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "operations", "attestation", "pyspec_tests");
    return operationsProcessingSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process attestatorSlashing pre={0} -> post={1}. {arguments}")
  @MethodSource("attestaterSlashingSetup")
  void processAttestatorSlashing(
      operations_processing.Context state, operations_processing.Context attestation)
      throws Exception {
    System.out.println("c.path:" + state.path);
    BlockProcessorUtil.process_attester_slashings(
        (BeaconState) state.obj, List.of((AttesterSlashing) attestation.obj));
    //    assertEquals(pre, plus);
  }

  @MustBeClosed
  static Stream<Arguments> attestaterSlashingSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "operations", "attester_slashing", "pyspec_tests");
    return attestorSlashingProcessingSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process blockHeader pre={0} -> post={1}. {arguments}")
  @MethodSource("blockHeaderSetup")
  void processBlockHeader(
      operations_processing.Context state, operations_processing.Context attestation)
      throws Exception {
    System.out.println("c.path:" + state.path);
    BlockProcessorUtil.process_block_header((BeaconState) state.obj, (BeaconBlock) attestation.obj);
    //    assertEquals(pre, plus);
  }

  @MustBeClosed
  static Stream<Arguments> blockHeaderSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "operations", "block_header", "pyspec_tests");
    return blockHeaderProcessingSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process deposit pre={0} -> post={1}. {arguments}")
  @MethodSource("depositSetup")
  void processDeposit(operations_processing.Context state, operations_processing.Context deposit)
      throws Exception {
    System.out.println("c.path:" + state.path);
    BlockProcessorUtil.process_deposits((BeaconState) state.obj, List.of((Deposit) deposit.obj));
    //    assertEquals(pre, plus);
  }

  @MustBeClosed
  static Stream<Arguments> depositSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "operations", "deposit", "pyspec_tests");
    return depositProcessingSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process proposer slashing pre={0} -> post={1}. {arguments}")
  @MethodSource("proposerSlashingSetup")
  void processProposerSlashing(
      operations_processing.Context state, operations_processing.Context proposerSlashing)
      throws Exception {
    System.out.println("c.path:" + state.path);
    BlockProcessorUtil.process_proposer_slashings(
        (BeaconState) state.obj, List.of((ProposerSlashing) proposerSlashing.obj));
    //    assertEquals(pre, plus);
  }

  @MustBeClosed
  static Stream<Arguments> proposerSlashingSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "operations", "proposer_slashing", "pyspec_tests");
    return proposerSlashingProcessingSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process voluntary exit pre={0} -> post={1}. {arguments}")
  @MethodSource("voluntaryExistSetup")
  void processVoluntaryExit(
      operations_processing.Context state, operations_processing.Context voluntaryExit)
      throws Exception {
    System.out.println("c.path:" + state.path);
    BlockProcessorUtil.process_voluntary_exits(
        (BeaconState) state.obj, List.of((VoluntaryExit) voluntaryExit.obj));
    //    assertEquals(pre, plus);
  }

  @MustBeClosed
  static Stream<Arguments> voluntaryExistSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "operations", "voluntary_exit", "pyspec_tests");
    return voluntaryExitProcessingSetup(path, configPath);
  }
}
