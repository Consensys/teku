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

package tech.pegasys.teku.ethereum.executionlayer;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV2;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV2;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class CapellaExecutionClientHandler extends BellatrixExecutionClientHandler
    implements ExecutionClientHandler {
  private static final Logger LOG = LogManager.getLogger();

  public CapellaExecutionClientHandler(
      final Spec spec, final ExecutionEngineClient executionEngineClient) {
    super(spec, executionEngineClient);
  }

  @Override
  public SafeFuture<ExecutionPayload> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    LOG.trace(
        "calling engineGetPayloadV2(payloadId={}, slot={})",
        executionPayloadContext.getPayloadId(),
        slot);
    return executionEngineClient
        .getPayloadV2(executionPayloadContext.getPayloadId())
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsCapella.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getExecutionPayloadSchema()),
            ExecutionPayloadV2::asInternalExecutionPayload)
        .thenPeek(
            executionPayload ->
                LOG.trace(
                    "engineGetPayloadV2(payloadId={}, slot={}) -> {}",
                    executionPayloadContext.getPayloadId(),
                    slot,
                    executionPayload));
  }

  @Override
  public SafeFuture<tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult>
      engineForkChoiceUpdated(
          final ForkChoiceState forkChoiceState,
          final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {

    LOG.trace(
        "calling engineForkChoiceUpdatedV2(forkChoiceState={}, payloadAttributes={})",
        forkChoiceState,
        payloadBuildingAttributes);
    return executionEngineClient
        .forkChoiceUpdatedV2(
            ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState),
            PayloadAttributesV2.fromInternalPayloadBuildingAttributesV2(payloadBuildingAttributes))
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(ForkChoiceUpdatedResult::asInternalExecutionPayload)
        .thenPeek(
            forkChoiceUpdatedResult ->
                LOG.trace(
                    "engineForkChoiceUpdatedV2(forkChoiceState={}, payloadAttributes={}) -> {}",
                    forkChoiceState,
                    payloadBuildingAttributes,
                    forkChoiceUpdatedResult));
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    LOG.trace("calling engineNewPayloadV2(executionPayload={})", executionPayload);
    return executionEngineClient
        .newPayloadV2(ExecutionPayloadV2.fromInternalExecutionPayload(executionPayload))
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(PayloadStatusV1::asInternalExecutionPayload)
        .thenPeek(
            payloadStatus ->
                LOG.trace(
                    "engineNewPayloadV2(executionPayload={}) -> {}",
                    executionPayload,
                    payloadStatus))
        .exceptionally(PayloadStatus::failedExecution);
  }
}
