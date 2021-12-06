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

package tech.pegasys.teku.reference.common.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

public class OperationsTestExecutor<T extends SszData> implements TestExecutor {

  public static final String EXPECTED_STATE_FILE = "post.ssz_snappy";

  private enum Operation {
    ATTESTER_SLASHING,
    PROPOSER_SLASHING,
    PROCESS_BLOCK_HEADER,
    DEPOSIT,
    VOLUNTARY_EXIT,
    ATTESTATION,
    SYNC_AGGREGATE,
    EXECUTION_PAYLOAD
  }

  public static ImmutableMap<String, TestExecutor> OPERATIONS_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put(
              "operations/attester_slashing",
              new OperationsTestExecutor<>(
                  "attester_slashing.ssz_snappy", Operation.ATTESTER_SLASHING))
          .put(
              "operations/proposer_slashing",
              new OperationsTestExecutor<>(
                  "proposer_slashing.ssz_snappy", Operation.PROPOSER_SLASHING))
          .put(
              "operations/block_header",
              new OperationsTestExecutor<>("block.ssz_snappy", Operation.PROCESS_BLOCK_HEADER))
          .put(
              "operations/deposit",
              new OperationsTestExecutor<>("deposit.ssz_snappy", Operation.DEPOSIT))
          .put(
              "operations/voluntary_exit",
              new OperationsTestExecutor<>("voluntary_exit.ssz_snappy", Operation.VOLUNTARY_EXIT))
          .put(
              "operations/attestation",
              new OperationsTestExecutor<>("attestation.ssz_snappy", Operation.ATTESTATION))
          .put(
              "operations/sync_aggregate",
              new OperationsTestExecutor<>("sync_aggregate.ssz_snappy", Operation.SYNC_AGGREGATE))
          .put(
              "operations/sync_aggregate_random",
              new OperationsTestExecutor<>("sync_aggregate.ssz_snappy", Operation.SYNC_AGGREGATE))
          .put(
              "operations/execution_payload",
              new OperationsTestExecutor<>(
                  "execution_payload.ssz_snappy", Operation.EXECUTION_PAYLOAD))
          .build();

  private final String dataFileName;
  private final Operation operation;

  public OperationsTestExecutor(final String dataFileName, final Operation operation) {
    this.dataFileName = dataFileName;
    this.operation = operation;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz_snappy");

    final DefaultOperationProcessor standardProcessor =
        new DefaultOperationProcessor(testDefinition.getSpec());
    runProcessor(standardProcessor, testDefinition, preState);
  }

  private void runProcessor(
      final OperationProcessor processor,
      final TestDefinition testDefinition,
      final BeaconState preState)
      throws Exception {
    if (testDefinition.getTestDirectory().resolve(EXPECTED_STATE_FILE).toFile().exists()) {
      assertOperationSuccessful(processor, testDefinition, preState);
    } else {
      assertOperationInvalid(testDefinition, processor, preState);
    }
  }

  private void assertOperationSuccessful(
      final OperationProcessor processor,
      final TestDefinition testDefinition,
      final BeaconState preState)
      throws Exception {
    final BeaconState expectedState = loadStateFromSsz(testDefinition, EXPECTED_STATE_FILE);
    final BeaconState result = applyOperation(testDefinition, processor, preState);
    assertThat(result).isEqualTo(expectedState);
  }

  private void assertOperationInvalid(
      final TestDefinition testDefinition,
      final OperationProcessor processor,
      final BeaconState preState) {
    assertThatThrownBy(() -> applyOperation(testDefinition, processor, preState))
        .isInstanceOf(BlockProcessingException.class);
  }

  private BeaconState applyOperation(
      final TestDefinition testDefinition,
      final OperationProcessor processor,
      final BeaconState preState)
      throws Exception {
    return preState.updated(state -> processOperation(testDefinition, state, processor));
  }

  private void processOperation(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor)
      throws Exception {
    switch (operation) {
      case ATTESTER_SLASHING:
        final AttesterSlashing attesterSlashing =
            loadSsz(testDefinition, dataFileName, AttesterSlashing.SSZ_SCHEMA);
        processor.processAttesterSlashing(state, attesterSlashing);
        break;
      case PROPOSER_SLASHING:
        final ProposerSlashing proposerSlashing =
            loadSsz(testDefinition, dataFileName, ProposerSlashing.SSZ_SCHEMA);
        processor.processProposerSlashing(state, proposerSlashing);
        break;
      case PROCESS_BLOCK_HEADER:
        final BeaconBlockSummary blockHeader =
            loadSsz(
                testDefinition,
                dataFileName,
                testDefinition.getSpec().getGenesisSchemaDefinitions().getBeaconBlockSchema());
        processor.processBlockHeader(state, blockHeader);
        break;
      case DEPOSIT:
        final Deposit deposit = loadSsz(testDefinition, dataFileName, Deposit.SSZ_SCHEMA);
        processor.processDeposit(state, deposit);
        break;
      case VOLUNTARY_EXIT:
        final SignedVoluntaryExit voluntaryExit =
            loadSsz(testDefinition, dataFileName, SignedVoluntaryExit.SSZ_SCHEMA);
        processor.processVoluntaryExit(state, voluntaryExit);
        break;
      case ATTESTATION:
        final Attestation attestation =
            loadSsz(testDefinition, dataFileName, Attestation.SSZ_SCHEMA);
        processor.processAttestation(state, attestation);
        break;
      case SYNC_AGGREGATE:
        final SyncAggregate syncAggregate =
            loadSsz(
                testDefinition,
                dataFileName,
                BeaconBlockBodySchemaAltair.required(
                        testDefinition
                            .getSpec()
                            .getGenesisSchemaDefinitions()
                            .getBeaconBlockBodySchema())
                    .getSyncAggregateSchema());
        processor.processSyncCommittee(state, syncAggregate);
        break;
      case EXECUTION_PAYLOAD:
        final ExecutionMeta executionMeta =
            loadYaml(testDefinition, "execution.yaml", ExecutionMeta.class);
        final ExecutionPayload payload =
            loadSsz(
                testDefinition,
                dataFileName,
                testDefinition
                    .getSpec()
                    .getGenesisSchemaDefinitions()
                    .toVersionMerge()
                    .orElseThrow()
                    .getExecutionPayloadSchema());
        processor.processExecutionPayload(
            state,
            payload,
            (latestExecutionPayloadHeader, payloadToExecute) -> executionMeta.executionValid);
        break;
    }
  }

  private static class ExecutionMeta {
    @JsonProperty(value = "execution_valid", required = true)
    private boolean executionValid;
  }
}
