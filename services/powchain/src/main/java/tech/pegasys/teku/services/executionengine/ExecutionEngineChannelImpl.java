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

package tech.pegasys.teku.services.executionengine;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.services.executionengine.client.ExecutionEngineClient;
import tech.pegasys.teku.services.executionengine.client.Web3JExecutionEngineClient;
import tech.pegasys.teku.services.executionengine.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.services.executionengine.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.services.executionengine.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.services.executionengine.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.services.executionengine.client.schema.Response;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.ssz.type.Bytes8;

public class ExecutionEngineChannelImpl implements ExecutionEngineChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineClient executionEngineClient;
  private final ExecutionPayloadSchema executionPayloadSchema;

  public static ExecutionEngineChannelImpl create(String eeEndpoint, Spec spec) {
    checkNotNull(eeEndpoint);
    return new ExecutionEngineChannelImpl(new Web3JExecutionEngineClient(eeEndpoint), spec);
  }

  public ExecutionEngineChannelImpl(ExecutionEngineClient executionEngineClient, Spec spec) {
    this.executionPayloadSchema =
        spec.forMilestone(SpecMilestone.MERGE)
            .getSchemaDefinitions()
            .toVersionMerge()
            .orElseThrow()
            .getExecutionPayloadSchema();
    this.executionEngineClient = executionEngineClient;
  }

  private static <K> K unwrapResponseOrThrow(Response<K> response) {
    checkArgument(
        response.getReason() == null, "Invalid remote response: %s", response.getReason());
    return response.getPayload();
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(Bytes32 blockHash) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("calling getPowBlock(blockHash={})", blockHash.toHexString());
    }
    return executionEngineClient
        .getPowBlock(blockHash)
        .thenPeek(
            powBlock -> {
              if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "getPowBlock(blockHash={}) -> {}",
                    blockHash.toHexString(),
                    powBlock.toString());
              }
            });
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("calling getPowChainHead()");
    }
    return executionEngineClient
        .getPowChainHead()
        .thenPeek(
            powBlock -> {
              if (LOG.isDebugEnabled()) {
                LOG.debug("getPowChainHead() -> {}", powBlock.toString());
              }
            });
  }

  @Override
  public SafeFuture<tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult>
      forkChoiceUpdated(
          ForkChoiceState forkChoiceState, Optional<PayloadAttributes> payloadAttributes) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "calling forkChoiceUpdated(forkChoiceState={}, payloadAttributes={})",
          forkChoiceState.toString(),
          payloadAttributes.toString());
    }
    return executionEngineClient
        .forkChoiceUpdated(
            ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState),
            PayloadAttributesV1.fromInternalForkChoiceState(payloadAttributes))
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenApply(ForkChoiceUpdatedResult::asInternalExecutionPayload)
        .thenPeek(
            forkChoiceUpdatedResult -> {
              if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "forkChoiceUpdated(forkChoiceState={}, payloadAttributes={}) -> {}",
                    forkChoiceState.toString(),
                    payloadAttributes.toString(),
                    forkChoiceUpdatedResult.toString());
              }
            });
  }

  @Override
  public SafeFuture<ExecutionPayload> getPayload(Bytes8 payloadId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("calling getPayload(payloadId={})", payloadId.toString());
    }
    return executionEngineClient
        .getPayload(payloadId)
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenApply(
            executionPayloadV1 ->
                executionPayloadV1.asInternalExecutionPayload(executionPayloadSchema))
        .thenPeek(
            executionPayload -> {
              if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "getPayload(payloadId={}) -> {}",
                    payloadId.toString(),
                    executionPayload.toString());
              }
            });
  }

  @Override
  public SafeFuture<ExecutePayloadResult> executePayload(ExecutionPayload executionPayload) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("calling executePayload(executionPayload={})", executionPayload.toString());
    }
    return executionEngineClient
        .executePayload(ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload))
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenApply(
            tech.pegasys.teku.services.executionengine.client.schema.ExecutePayloadResult
                ::asInternalExecutionPayload)
        .thenPeek(
            executePayloadResult -> {
              if (LOG.isDebugEnabled()) {
                LOG.debug(
                    "executePayload(executionPayload={}) -> {}",
                    executionPayload.toString(),
                    executePayloadResult.toString());
              }
            });
  }
}
