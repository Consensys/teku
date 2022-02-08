/*
 * Copyright 2021 ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionlayer.client.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionlayer.client.KintsugiWeb3JExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionlayer.client.Web3JExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.spec.executionengine.PayloadStatus;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class ExecutionEngineChannelImpl implements ExecutionEngineChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineClient executionEngineClient;
  private final Spec spec;

  public static ExecutionEngineChannelImpl create(
      final String eeEndpoint,
      final Spec spec,
      final TimeProvider timeProvider,
      final Version version) {
    checkNotNull(eeEndpoint);
    checkNotNull(version);
    return new ExecutionEngineChannelImpl(
        createEngineClient(eeEndpoint, timeProvider, version), spec);
  }

  private static ExecutionEngineClient createEngineClient(
      final String eeEndpoint, final TimeProvider timeProvider, final Version version) {
    LOG.info("Execution Engine version: {}", version);
    switch (version) {
      case kiln:
        return new Web3JExecutionEngineClient(eeEndpoint, timeProvider);
      case kintsugi:
      default:
        return new KintsugiWeb3JExecutionEngineClient(eeEndpoint, timeProvider);
    }
  }

  private ExecutionEngineChannelImpl(ExecutionEngineClient executionEngineClient, Spec spec) {
    this.spec = spec;
    this.executionEngineClient = executionEngineClient;
  }

  private static <K> K unwrapResponseOrThrow(Response<K> response) {
    checkArgument(
        response.getErrorMessage() == null,
        "Invalid remote response: %s",
        response.getErrorMessage());
    return response.getPayload();
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(final Bytes32 blockHash) {
    LOG.trace("calling getPowBlock(blockHash={})", blockHash);

    return executionEngineClient
        .getPowBlock(blockHash)
        .thenPeek(powBlock -> LOG.trace("getPowBlock(blockHash={}) -> {}", blockHash, powBlock));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    LOG.trace("calling getPowChainHead()");

    return executionEngineClient
        .getPowChainHead()
        .thenPeek(powBlock -> LOG.trace("getPowChainHead() -> {}", powBlock));
  }

  @Override
  public SafeFuture<tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult>
      forkChoiceUpdated(
          final ForkChoiceState forkChoiceState,
          final Optional<PayloadAttributes> payloadAttributes) {

    LOG.trace(
        "calling forkChoiceUpdated(forkChoiceState={}, payloadAttributes={})",
        forkChoiceState,
        payloadAttributes);

    return executionEngineClient
        .forkChoiceUpdated(
            ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState),
            PayloadAttributesV1.fromInternalForkChoiceState(payloadAttributes))
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenApply(ForkChoiceUpdatedResult::asInternalExecutionPayload)
        .thenPeek(
            forkChoiceUpdatedResult ->
                LOG.trace(
                    "forkChoiceUpdated(forkChoiceState={}, payloadAttributes={}) -> {}",
                    forkChoiceState,
                    payloadAttributes,
                    forkChoiceUpdatedResult));
  }

  @Override
  public SafeFuture<ExecutionPayload> getPayload(final Bytes8 payloadId, final UInt64 slot) {
    LOG.trace("calling getPayload(payloadId={}, slot={})", payloadId, slot);

    return executionEngineClient
        .getPayload(payloadId)
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getExecutionPayloadSchema()),
            ExecutionPayloadV1::asInternalExecutionPayload)
        .thenPeek(
            executionPayload ->
                LOG.trace(
                    "getPayload(payloadId={}, slot={}) -> {}", payloadId, slot, executionPayload));
  }

  @Override
  public SafeFuture<PayloadStatus> newPayload(final ExecutionPayload executionPayload) {
    LOG.trace("calling newPayload(executionPayload={})", executionPayload);

    return executionEngineClient
        .newPayload(ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload))
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenApply(PayloadStatusV1::asInternalExecutionPayload)
        .thenPeek(
            payloadStatus ->
                LOG.trace("newPayload(executionPayload={}) -> {}", executionPayload, payloadStatus))
        .exceptionally(PayloadStatus::failedExecution);
  }
}
