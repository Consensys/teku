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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethods;
import tech.pegasys.teku.ethereum.executionclient.methods.JsonRpcRequestParams;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadWithValue;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;

public class ExecutionClientHandlerImpl implements ExecutionClientHandler {

  private final ExecutionJsonRpcMethodsResolver methodsResolver;

  public ExecutionClientHandlerImpl(final ExecutionJsonRpcMethodsResolver methodsResolver) {
    this.methodsResolver = methodsResolver;
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().add(blockHash).build();

    return methodsResolver
        .getMethod(EngineApiMethods.ETH_GET_BLOCK_BY_HASH, PowBlock.class)
        .execute(params)
        .thenApply(Optional::ofNullable);
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    // uses LATEST as default block parameter on Eth1 JSON-RPC call
    return methodsResolver
        .getMethod(EngineApiMethods.ETH_GET_BLOCK_BY_NUMBER, PowBlock.class)
        .execute(JsonRpcRequestParams.NO_PARAMS);
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

    return methodsResolver
        .getMethod(EngineApiMethods.ENGINE_FORK_CHOICE_UPDATED, ForkChoiceUpdatedResult.class)
        .execute(params);
  }

  @Override
  public SafeFuture<ExecutionPayloadWithValue> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(slot).build();

    return methodsResolver
        .getMethod(EngineApiMethods.ENGINE_GET_PAYLOAD, ExecutionPayloadWithValue.class)
        .execute(params);
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();

    return methodsResolver
        .getMethod(EngineApiMethods.ENGINE_NEW_PAYLOAD, PayloadStatus.class)
        .execute(params);
  }

  @Override
  public SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      final TransitionConfiguration transitionConfiguration) {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(transitionConfiguration).build();

    return methodsResolver
        .getMethod(
            EngineApiMethods.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION,
            TransitionConfiguration.class)
        .execute(params);
  }
}
