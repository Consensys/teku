/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.reference.phase0.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.ssz.backing.SszData;

public class OperationsTestExecutor<T extends SszData> implements TestExecutor {

  public static final String EXPECTED_STATE_FILE = "post.ssz";

  private enum Operation {
    ATTESTER_SLASHING,
    PROPOSER_SLASHING,
    PROCESS_BLOCK_HEADER,
    DEPOSIT,
    VOLUNTARY_EXIT,
    ATTESTATION
  }

  public static ImmutableMap<String, TestExecutor> OPERATIONS_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put(
              "operations/attester_slashing",
              new OperationsTestExecutor<>("attester_slashing.ssz", Operation.ATTESTER_SLASHING))
          .put(
              "operations/proposer_slashing",
              new OperationsTestExecutor<>("proposer_slashing.ssz", Operation.PROPOSER_SLASHING))
          .put(
              "operations/block_header",
              new OperationsTestExecutor<>("block.ssz", Operation.PROCESS_BLOCK_HEADER))
          .put("operations/deposit", new OperationsTestExecutor<>("deposit.ssz", Operation.DEPOSIT))
          .put(
              "operations/voluntary_exit",
              new OperationsTestExecutor<>("voluntary_exit.ssz", Operation.VOLUNTARY_EXIT))
          .put(
              "operations/attestation",
              new OperationsTestExecutor<>("attestation.ssz", Operation.ATTESTATION))
          .build();

  private final String dataFileName;
  private final Operation operation;

  public OperationsTestExecutor(final String dataFileName, final Operation operation) {
    this.dataFileName = dataFileName;
    this.operation = operation;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz");
    final Path dataPath = testDefinition.getTestDirectory().resolve(dataFileName);

    final DefaultOperationProcessor standardProcessor =
        new DefaultOperationProcessor(testDefinition.getSpecProvider());
    runProcessor(standardProcessor, testDefinition, preState, dataPath);

    final DeprecatedOperationProcessor deprecatedProcessor = new DeprecatedOperationProcessor();
    runProcessor(deprecatedProcessor, testDefinition, preState, dataPath);
  }

  private void runProcessor(
      final OperationProcessor processor,
      final TestDefinition testDefinition,
      final BeaconState preState,
      final Path dataPath)
      throws Exception {
    if (testDefinition.getTestDirectory().resolve(EXPECTED_STATE_FILE).toFile().exists()) {
      assertOperationSuccessful(processor, testDefinition, preState, dataPath);
    } else {
      assertOperationInvalid(processor, preState, dataPath);
    }
  }

  private void assertOperationSuccessful(
      final OperationProcessor processor,
      final TestDefinition testDefinition,
      final BeaconState preState,
      final Path dataPath)
      throws Exception {
    final BeaconState expectedState = loadStateFromSsz(testDefinition, EXPECTED_STATE_FILE);
    final BeaconState result = applyOperation(processor, preState, dataPath);
    assertThat(result).isEqualTo(expectedState);
  }

  private void assertOperationInvalid(
      final OperationProcessor processor, final BeaconState preState, final Path dataPath) {
    assertThatThrownBy(() -> applyOperation(processor, preState, dataPath))
        .isInstanceOf(BlockProcessingException.class);
  }

  private BeaconState applyOperation(
      final OperationProcessor processor, final BeaconState preState, final Path dataPath)
      throws Exception {
    return preState.updated(state -> processOperation(state, dataPath, processor));
  }

  private void processOperation(
      final MutableBeaconState state, final Path dataPath, final OperationProcessor processor)
      throws Exception {
    switch (operation) {
      case ATTESTER_SLASHING:
        final AttesterSlashing attesterSlashing =
            AttesterSlashing.SSZ_SCHEMA.sszDeserialize(Bytes.wrap(Files.readAllBytes(dataPath)));
        processor.processAttesterSlashing(state, attesterSlashing);
        break;
      case PROPOSER_SLASHING:
        final ProposerSlashing proposerSlashing =
            ProposerSlashing.SSZ_SCHEMA.sszDeserialize(Bytes.wrap(Files.readAllBytes(dataPath)));
        processor.processProposerSlashing(state, proposerSlashing);
        break;
      case PROCESS_BLOCK_HEADER:
        final BeaconBlockSummary blockHeader =
            BeaconBlock.getSszSchema().sszDeserialize(Bytes.wrap(Files.readAllBytes(dataPath)));
        processor.processBlockHeader(state, blockHeader);
        break;
      case DEPOSIT:
        final Deposit deposit =
            Deposit.SSZ_SCHEMA.sszDeserialize(Bytes.wrap(Files.readAllBytes(dataPath)));
        processor.processDeposit(state, deposit);
        break;
      case VOLUNTARY_EXIT:
        final SignedVoluntaryExit voluntaryExit =
            SignedVoluntaryExit.SSZ_SCHEMA.sszDeserialize(Bytes.wrap(Files.readAllBytes(dataPath)));
        processor.processVoluntaryExit(state, voluntaryExit);
        break;
      case ATTESTATION:
        final Attestation attestation =
            Attestation.SSZ_SCHEMA.sszDeserialize(Bytes.wrap(Files.readAllBytes(dataPath)));
        processor.processAttestation(state, attestation);
        break;
    }
  }
}
