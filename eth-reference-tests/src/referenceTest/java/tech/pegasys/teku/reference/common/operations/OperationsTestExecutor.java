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

package tech.pegasys.teku.reference.common.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.TotalBalances;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.statetransition.validation.AttesterSlashingValidator;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.ProposerSlashingValidator;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;
import tech.pegasys.teku.statetransition.validation.VoluntaryExitValidator;

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
    EXECUTION_PAYLOAD,
    BLS_TO_EXECUTION_CHANGE,
    WITHDRAWAL
  }

  public static final ImmutableMap<String, TestExecutor> OPERATIONS_TEST_TYPES =
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
          .put(
              "operations/bls_to_execution_change",
              new OperationsTestExecutor<>(
                  "address_change.ssz_snappy", Operation.BLS_TO_EXECUTION_CHANGE))
          .put(
              "operations/withdrawals",
              new OperationsTestExecutor<>("execution_payload.ssz_snappy", Operation.WITHDRAWAL))
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
    final boolean isOperationValid =
        testDefinition.getTestDirectory().resolve(EXPECTED_STATE_FILE).toFile().exists();
    if (isOperationValid) {
      assertOperationSuccessful(processor, testDefinition, preState);
    } else {
      assertOperationInvalid(testDefinition, processor, preState);
    }

    checkBlockInclusionValidation(testDefinition, preState, isOperationValid);
  }

  private void assertOperationSuccessful(
      final OperationProcessor processor,
      final TestDefinition testDefinition,
      final BeaconState preState)
      throws Exception {
    initProgressiveBalances(testDefinition, preState);

    final BeaconState expectedState = loadStateFromSsz(testDefinition, EXPECTED_STATE_FILE);
    final BeaconState result = applyOperation(testDefinition, processor, preState);
    assertThat(result).isEqualTo(expectedState);

    verifyProgressiveBalances(testDefinition, result);
  }

  private void initProgressiveBalances(
      final TestDefinition testDefinition, final BeaconState state) {
    final Spec spec = testDefinition.getSpec();
    spec.atSlot(state.getSlot())
        .getEpochProcessor()
        .initProgressiveTotalBalancesIfRequired(state, calculateTotalBalances(spec, state));
  }

  /**
   * Checks the progressive balances match the non-progressive results both for the immediate state,
   * and after it goes through another epoch transition.
   */
  private void verifyProgressiveBalances(
      final TestDefinition testDefinition, final BeaconState result)
      throws SlotProcessingException, EpochProcessingException {
    if (testDefinition.getSpec().atSlot(result.getSlot()).getMilestone() == SpecMilestone.PHASE0) {
      // Progressive balances not used in Phase0
      return;
    }
    final Spec spec = testDefinition.getSpec();
    assertTotalBalances(spec, result);
    final UInt64 firstSlotOfNextEpoch =
        spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(result.getSlot()).plus(1));
    final BeaconState nextEpochState = spec.processSlots(result, firstSlotOfNextEpoch);
    assertTotalBalances(spec, nextEpochState);
  }

  private void assertTotalBalances(final Spec spec, final BeaconState state) {
    final Optional<TotalBalances> maybeProgressiveBalances =
        BeaconStateCache.getTransitionCaches(state)
            .getProgressiveTotalBalances()
            .getTotalBalances(spec.getSpecConfig(state.getSlot()));
    assertThat(maybeProgressiveBalances).isPresent();
    final TotalBalances progressiveBalances = maybeProgressiveBalances.get();

    final TotalBalances totalBalances = calculateTotalBalances(spec, state);
    assertThat(progressiveBalances).isEqualTo(totalBalances);
  }

  private TotalBalances calculateTotalBalances(final Spec spec, final BeaconState nextEpochState) {
    return spec.atSlot(nextEpochState.getSlot())
        .getValidatorStatusFactory()
        .createValidatorStatuses(nextEpochState)
        .getTotalBalances();
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
        final AttesterSlashing attesterSlashing = loadAttesterSlashing(testDefinition);
        processor.processAttesterSlashing(state, attesterSlashing);
        break;
      case PROPOSER_SLASHING:
        final ProposerSlashing proposerSlashing = loadProposerSlashing(testDefinition);
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
        final SignedVoluntaryExit voluntaryExit = loadVoluntaryExit(testDefinition);
        processor.processVoluntaryExit(state, voluntaryExit);
        break;
      case ATTESTATION:
        final Attestation attestation =
            loadSsz(
                testDefinition,
                dataFileName,
                testDefinition.getSpec().getGenesisSchemaDefinitions().getAttestationSchema());
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

        final SchemaDefinitionsBellatrix schemaDefinitionsBellatrix =
            testDefinition
                .getSpec()
                .getGenesisSchemaDefinitions()
                .toVersionBellatrix()
                .orElseThrow();
        final ExecutionPayload executionPayload =
            loadSsz(
                testDefinition,
                dataFileName,
                schemaDefinitionsBellatrix.getExecutionPayloadSchema());

        final ExecutionPayloadHeader executionPayloadHeader =
            schemaDefinitionsBellatrix
                .getExecutionPayloadHeaderSchema()
                .createFromExecutionPayload(executionPayload);

        processor.processExecutionPayload(
            state,
            executionPayloadHeader,
            Optional.of(executionPayload),
            Optional.of(
                (latestExecutionPayloadHeader, payloadToExecute) -> executionMeta.executionValid));
        break;
      case BLS_TO_EXECUTION_CHANGE:
        processBlsToExecutionChange(testDefinition, state, processor);
        break;
      case WITHDRAWAL:
        processWithdrawal(testDefinition, state, processor);
        break;
      default:
        throw new UnsupportedOperationException(
            "Operation " + operation + " not implemented in OperationTestExecutor");
    }
  }

  private void processWithdrawal(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor)
      throws BlockProcessingException {
    final SchemaDefinitionsCapella schemaDefinitionsCapella =
        SchemaDefinitionsCapella.required(testDefinition.getSpec().getGenesisSchemaDefinitions());
    final ExecutionPayload executionPayload =
        loadSsz(testDefinition, dataFileName, schemaDefinitionsCapella.getExecutionPayloadSchema());
    processor.processWithdrawals(state, executionPayload);
  }

  private void processBlsToExecutionChange(
      final TestDefinition testDefinition,
      final MutableBeaconState state,
      final OperationProcessor processor)
      throws BlockProcessingException {
    final SignedBlsToExecutionChange blsToExecutionChange =
        loadBlsToExecutionChange(testDefinition);
    processor.processBlsToExecutionChange(state, blsToExecutionChange);
  }

  private SignedVoluntaryExit loadVoluntaryExit(final TestDefinition testDefinition) {
    return loadSsz(testDefinition, dataFileName, SignedVoluntaryExit.SSZ_SCHEMA);
  }

  private ProposerSlashing loadProposerSlashing(final TestDefinition testDefinition) {
    return loadSsz(testDefinition, dataFileName, ProposerSlashing.SSZ_SCHEMA);
  }

  private AttesterSlashing loadAttesterSlashing(final TestDefinition testDefinition) {
    return loadSsz(
        testDefinition,
        dataFileName,
        testDefinition.getSpec().getGenesisSchemaDefinitions().getAttesterSlashingSchema());
  }

  private SignedBlsToExecutionChange loadBlsToExecutionChange(final TestDefinition testDefinition) {
    final SchemaDefinitionsCapella schemaDefinitionsCapella =
        SchemaDefinitionsCapella.required(testDefinition.getSpec().getGenesisSchemaDefinitions());
    return loadSsz(
        testDefinition,
        dataFileName,
        schemaDefinitionsCapella.getSignedBlsToExecutionChangeSchema());
  }

  public void checkBlockInclusionValidation(
      final TestDefinition testDefinition, final BeaconState state, final boolean expectInclusion) {
    final Spec spec = testDefinition.getSpec();
    switch (operation) {
      case ATTESTER_SLASHING:
        final AttesterSlashing attesterSlashing = loadAttesterSlashing(testDefinition);
        final AttesterSlashingValidator validator = new AttesterSlashingValidator(null, spec);
        checkValidationForBlockInclusion(validator, state, attesterSlashing, expectInclusion);
        break;
      case PROPOSER_SLASHING:
        final ProposerSlashing proposerSlashing = loadProposerSlashing(testDefinition);
        final ProposerSlashingValidator proposerValidator =
            new ProposerSlashingValidator(spec, null);
        checkValidationForBlockInclusion(
            proposerValidator, state, proposerSlashing, expectInclusion);
        break;
      case VOLUNTARY_EXIT:
        final SignedVoluntaryExit voluntaryExit = loadVoluntaryExit(testDefinition);
        final VoluntaryExitValidator voluntaryExitValidator =
            new VoluntaryExitValidator(spec, null);
        checkValidationForBlockInclusion(
            voluntaryExitValidator, state, voluntaryExit, expectInclusion);
        break;
      case BLS_TO_EXECUTION_CHANGE:
        final SignedBlsToExecutionChangeValidator blsToExecutionChangeValidator =
            new SignedBlsToExecutionChangeValidator(spec, new SystemTimeProvider(), null);
        final SignedBlsToExecutionChange blsToExecutionChange =
            loadBlsToExecutionChange(testDefinition);
        checkValidationForBlockInclusion(
            blsToExecutionChangeValidator, state, blsToExecutionChange, expectInclusion);
        break;

      case PROCESS_BLOCK_HEADER:
      case DEPOSIT:
      case ATTESTATION:
      case SYNC_AGGREGATE:
      case EXECUTION_PAYLOAD:
      case WITHDRAWAL:
        // Not yet testing inclusion rules
        break;
    }
  }

  private <O> void checkValidationForBlockInclusion(
      final OperationValidator<O> validator,
      final BeaconState state,
      final O operation,
      final boolean expectInclusion) {
    final Optional<String> invalidReason =
        validator.validateForBlockInclusion(state, operation).map(OperationInvalidReason::describe);
    if (expectInclusion) {
      assertThat(invalidReason).isEmpty();
    } else {
      assertThat(invalidReason).isPresent();
    }
  }

  private static class ExecutionMeta {
    @JsonProperty(value = "execution_valid", required = true)
    private boolean executionValid;
  }
}
