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
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineForkChoiceUpdatedV1;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineNewPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.methods.JsonRpcRequestParams;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadWithValue;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

class BellatrixExecutionClientHandler implements ExecutionClientHandler {
  private static final Logger LOG = LogManager.getLogger();
  protected final Spec spec;
  protected final ExecutionEngineClient executionEngineClient;

  public BellatrixExecutionClientHandler(
      final Spec spec, final ExecutionEngineClient executionEngineClient) {
    this.spec = spec;
    this.executionEngineClient = executionEngineClient;
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    LOG.trace("calling eth1GetPowBlock(blockHash={})", blockHash);
    return executionEngineClient
        .getPowBlock(blockHash)
        .thenPeek(
            powBlock -> LOG.trace("eth1GetPowBlock(blockHash={}) -> {}", blockHash, powBlock));
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    LOG.trace("calling eth1GetPowChainHead()");
    return executionEngineClient
        .getPowChainHead()
        .thenPeek(powBlock -> LOG.trace("eth1GetPowChainHead() -> {}", powBlock));
  }

  @Override
  public SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      final ForkChoiceState forkChoiceState,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(forkChoiceState)
            .addOptional(payloadBuildingAttributes)
            .build();

    return new EngineForkChoiceUpdatedV1(executionEngineClient).execute(params);
  }

  @Override
  public SafeFuture<ExecutionPayloadWithValue> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    LOG.trace(
        "calling engineGetPayloadV1(payloadId={}, slot={})",
        executionPayloadContext.getPayloadId(),
        slot);
    return executionEngineClient
        .getPayloadV1(executionPayloadContext.getPayloadId())
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(
            payload -> {
              final ExecutionPayloadSchema<?> payloadSchema =
                  SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
                      .getExecutionPayloadSchema();
              return new ExecutionPayloadWithValue(
                  payload.asInternalExecutionPayload(payloadSchema), UInt256.ZERO);
            })
        .thenPeek(
            payloadAndValue ->
                LOG.trace(
                    "engineGetPayloadV1(payloadId={}, slot={}) -> {}",
                    executionPayloadContext.getPayloadId(),
                    slot,
                    payloadAndValue));
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();
    return new EngineNewPayloadV1(executionEngineClient).execute(params);
  }

  @Override
  public SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      final TransitionConfiguration transitionConfiguration) {
    LOG.trace(
        "calling engineExchangeTransitionConfiguration(transitionConfiguration={})",
        transitionConfiguration);

    return executionEngineClient
        .exchangeTransitionConfiguration(
            TransitionConfigurationV1.fromInternalTransitionConfiguration(transitionConfiguration))
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(TransitionConfigurationV1::asInternalTransitionConfiguration)
        .thenPeek(
            remoteTransitionConfiguration ->
                LOG.trace(
                    "engineExchangeTransitionConfiguration(transitionConfiguration={}) -> {}",
                    transitionConfiguration,
                    remoteTransitionConfiguration));
  }
}
