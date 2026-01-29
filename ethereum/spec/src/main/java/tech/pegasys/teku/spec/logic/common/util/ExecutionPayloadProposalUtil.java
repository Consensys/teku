/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.ExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadProcessor;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionPayloadProposalUtil {

  private final SchemaDefinitionsGloas schemaDefinitions;
  private final ExecutionPayloadProcessor executionPayloadProcessor;

  public ExecutionPayloadProposalUtil(
      final SchemaDefinitionsGloas schemaDefinitions,
      final ExecutionPayloadProcessor executionPayloadProcessor) {
    this.schemaDefinitions = schemaDefinitions;
    this.executionPayloadProcessor = executionPayloadProcessor;
  }

  public record ExecutionPayloadProposalData(
      ExecutionPayload executionPayload,
      ExecutionRequests executionRequests,
      SszList<SszKZGCommitment> kzgCommitments) {}

  public SafeFuture<ExecutionPayloadAndState> createNewUnsignedExecutionPayload(
      final UInt64 proposalSlot,
      final UInt64 builderIndex,
      final BeaconBlockAndState blockAndState,
      final SafeFuture<ExecutionPayloadProposalData> executionPayloadProposalDataFuture) {
    final SafeFuture<ExecutionPayloadEnvelope> newExecutionPayload =
        executionPayloadProposalDataFuture.thenApply(
            executionPayloadProposalData -> {
              // Create initial execution payload with some stubs
              final Bytes32 tmpStateRoot = Bytes32.ZERO;
              return schemaDefinitions
                  .getExecutionPayloadEnvelopeSchema()
                  .create(
                      executionPayloadProposalData.executionPayload,
                      executionPayloadProposalData.executionRequests,
                      builderIndex,
                      blockAndState.getRoot(),
                      proposalSlot,
                      executionPayloadProposalData.kzgCommitments,
                      tmpStateRoot);
            });
    return newExecutionPayload.thenApplyChecked(
        executionPayload -> {
          // Run state transition and set state root
          final BeaconState newState =
              blockAndState
                  .getState()
                  .updated(
                      state ->
                          executionPayloadProcessor.processUnsignedExecutionPayload(
                              state, executionPayload, Optional.empty()));
          return new ExecutionPayloadAndState(
              executionPayload.copyWithNewStateRoot(newState.hashTreeRoot()), newState);
        });
  }
}
