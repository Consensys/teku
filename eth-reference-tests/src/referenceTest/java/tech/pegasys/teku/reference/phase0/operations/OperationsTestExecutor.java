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
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.core.BlockProcessorUtil;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.core.lookup.IndexedAttestationProvider;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class OperationsTestExecutor<T> implements TestExecutor {

  public static final String EXPECTED_STATE_FILE = "post.ssz";
  public static ImmutableMap<String, TestExecutor> OPERATIONS_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put(
              "operations/attester_slashing",
              new OperationsTestExecutor<>(
                  "attester_slashing.ssz",
                  AttesterSlashing.class,
                  (state, data) ->
                      BlockProcessorUtil.process_attester_slashings(
                          state, SSZList.singleton(data))))
          .put(
              "operations/proposer_slashing",
              new OperationsTestExecutor<>(
                  "proposer_slashing.ssz",
                  ProposerSlashing.class,
                  (state, data) ->
                      BlockProcessorUtil.process_proposer_slashings(
                          state, SSZList.singleton(data))))
          .put(
              "operations/block_header",
              new OperationsTestExecutor<>(
                  "block.ssz", BeaconBlock.class, BlockProcessorUtil::process_block_header))
          .put(
              "operations/deposit",
              new OperationsTestExecutor<>(
                  "deposit.ssz",
                  Deposit.class,
                  (state, data) ->
                      BlockProcessorUtil.process_deposits(state, SSZList.singleton(data))))
          .put(
              "operations/voluntary_exit",
              new OperationsTestExecutor<>(
                  "voluntary_exit.ssz",
                  SignedVoluntaryExit.class,
                  (state, data) ->
                      BlockProcessorUtil.process_voluntary_exits(state, SSZList.singleton(data))))
          .put(
              "operations/attestation",
              new OperationsTestExecutor<>(
                  "attestation.ssz",
                  Attestation.class,
                  (state, data) ->
                      BlockProcessorUtil.process_attestations(
                          state,
                          SSZList.singleton(data),
                          IndexedAttestationProvider.DIRECT_PROVIDER)))
          .build();

  private final String dataFileName;
  private final Class<T> dataType;
  private final StateOperation<T> operation;

  public OperationsTestExecutor(
      final String dataFileName, final Class<T> dataType, final StateOperation<T> operation) {
    this.dataFileName = dataFileName;
    this.dataType = dataType;
    this.operation = operation;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz");
    final T data = loadSsz(testDefinition, dataFileName, dataType);
    if (testDefinition.getTestDirectory().resolve(EXPECTED_STATE_FILE).toFile().exists()) {
      assertOperationSuccessful(testDefinition, preState, data);
    } else {
      assertOperationInvalid(preState, data);
    }
  }

  private void assertOperationSuccessful(
      final TestDefinition testDefinition, final BeaconState preState, final T data)
      throws Exception {
    final BeaconState expectedState = loadStateFromSsz(testDefinition, EXPECTED_STATE_FILE);
    final BeaconState result = applyOperation(preState, data);
    assertThat(result).isEqualTo(expectedState);
  }

  private void assertOperationInvalid(final BeaconState preState, final T data) {
    assertThatThrownBy(() -> applyOperation(preState, data))
        .isInstanceOf(BlockProcessingException.class);
  }

  private BeaconState applyOperation(final BeaconState preState, final T data) throws Exception {
    return preState.updated(state -> operation.performOperation(state, data));
  }

  public interface StateOperation<T> {
    void performOperation(final MutableBeaconState state, final T data) throws Exception;
  }
}
