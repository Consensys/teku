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
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.methods.EngineApiMethod;
import tech.pegasys.teku.ethereum.executionclient.methods.JsonRpcRequestParams;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;

public class ExecutionClientHandlerImpl implements ExecutionClientHandler {

  private final Spec spec;
  private final ExecutionEngineClient executionEngineClient;
  private final EngineJsonRpcMethodsResolver engineMethodsResolver;

  public ExecutionClientHandlerImpl(
      final Spec spec,
      final ExecutionEngineClient executionEngineClient,
      final EngineJsonRpcMethodsResolver engineMethodsResolver) {
    this.spec = spec;
    this.executionEngineClient = executionEngineClient;
    this.engineMethodsResolver = engineMethodsResolver;
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    return executionEngineClient.getPowBlock(blockHash).thenApply(Optional::ofNullable);
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    // uses LATEST as default block parameter on Eth1 JSON-RPC call
    return executionEngineClient.getPowChainHead();
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

    return engineMethodsResolver
        .getMethod(
            EngineApiMethod.ENGINE_FORK_CHOICE_UPDATED,
            () -> {
              final UInt64 slot =
                  payloadBuildingAttributes
                      .map(PayloadBuildingAttributes::getBlockSlot)
                      .orElse(forkChoiceState.getHeadBlockSlot());
              return spec.atSlot(slot).getMilestone();
            },
            ForkChoiceUpdatedResult.class)
        .execute(params);
  }

  @Override
  public SafeFuture<GetPayloadResponse> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayloadContext).add(slot).build();

    return engineMethodsResolver
        .getMethod(
            EngineApiMethod.ENGINE_GET_PAYLOAD,
            () -> spec.atSlot(slot).getMilestone(),
            GetPayloadResponse.class)
        .execute(params);
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final NewPayloadRequest newPayloadRequest) {
    final ExecutionPayload executionPayload = newPayloadRequest.getExecutionPayload();
    final JsonRpcRequestParams.Builder paramsBuilder =
        new JsonRpcRequestParams.Builder()
            .add(executionPayload)
            .addOptional(newPayloadRequest.getVersionedHashes())
            .addOptional(newPayloadRequest.getParentBeaconBlockRoot());

    return engineMethodsResolver
        .getMethod(
            EngineApiMethod.ENGINE_NEW_PAYLOAD, executionPayload::getMilestone, PayloadStatus.class)
        .execute(paramsBuilder.build());
  }
}
