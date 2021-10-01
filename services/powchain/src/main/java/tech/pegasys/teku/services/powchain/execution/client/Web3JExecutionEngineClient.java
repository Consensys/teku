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

package tech.pegasys.teku.services.powchain.execution.client;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.powchain.execution.client.schema.ConsensusValidatedRequest;
import tech.pegasys.teku.services.powchain.execution.client.schema.ExecutePayloadResponse;
import tech.pegasys.teku.services.powchain.execution.client.schema.ExecutionPayload;
import tech.pegasys.teku.services.powchain.execution.client.schema.ForkchoiceUpdatedRequest;
import tech.pegasys.teku.services.powchain.execution.client.schema.GenericResponse;
import tech.pegasys.teku.services.powchain.execution.client.schema.PreparePayloadRequest;
import tech.pegasys.teku.services.powchain.execution.client.schema.PreparePayloadResponse;
import tech.pegasys.teku.services.powchain.execution.client.schema.Response;
import tech.pegasys.teku.services.powchain.execution.client.serializer.UInt64AsHexDeserializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.UInt64AsHexSerializer;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.ConsensusValidationResult;

public class Web3JExecutionEngineClient implements ExecutionEngineClient {

  private final Web3j eth1Web3j;
  private final HttpService eeWeb3jService;

  public Web3JExecutionEngineClient(String eth1Endpoint, Optional<String> maybeEeEndpoint) {
    HttpService eth1Web3jService = new HttpService(eth1Endpoint);
    this.eth1Web3j = Web3j.build(eth1Web3jService);
    this.eeWeb3jService = maybeEeEndpoint.map(HttpService::new).orElse(eth1Web3jService);
  }

  @Override
  public SafeFuture<Response<PreparePayloadResponse>> preparePayload(
      PreparePayloadRequest request) {
    Request<?, PreparePayloadWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_preparePayload",
            Collections.singletonList(request),
            eeWeb3jService,
            PreparePayloadWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ExecutionPayload>> getPayload(UInt64 payloadId) {
    Request<?, GetPayloadWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_getPayload",
            Collections.singletonList(UInt64AsHexSerializer.toHexString(payloadId)),
            eeWeb3jService,
            GetPayloadWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ExecutePayloadResponse>> executePayload(ExecutionPayload request) {
    Request<?, NewBlockWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_executePayload",
            Collections.singletonList(request),
            eeWeb3jService,
            NewBlockWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<GenericResponse>> forkchoiceUpdated(
      Bytes32 headBlockHash, Bytes32 finalizedBlockHash) {
    Request<?, GenericWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_forkchoiceUpdated",
            Collections.singletonList(
                new ForkchoiceUpdatedRequest(headBlockHash, finalizedBlockHash)),
            eeWeb3jService,
            GenericWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<GenericResponse>> consensusValidated(
      Bytes32 blockHash, String validationResult) {
    Request<?, GenericWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_consensusValidated",
            Collections.singletonList(
                new ConsensusValidatedRequest(
                    blockHash, ConsensusValidationResult.valueOf(validationResult))),
            eeWeb3jService,
            GenericWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Optional<EthBlock.Block>> getPowBlock(Bytes32 blockHash) {
    return SafeFuture.of(eth1Web3j.ethGetBlockByHash(blockHash.toHexString(), false).sendAsync())
        .thenApply(EthBlock::getBlock)
        .thenApply(Optional::ofNullable);
  }

  @Override
  public SafeFuture<EthBlock.Block> getPowChainHead() {
    return SafeFuture.of(
            eth1Web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).sendAsync())
        .thenApply(EthBlock::getBlock);
  }

  private <T> SafeFuture<Response<T>> doRequest(
      Request<?, ? extends org.web3j.protocol.core.Response<T>> web3jRequest) {
    CompletableFuture<Response<T>> responseFuture =
        web3jRequest
            .sendAsync()
            .handle(
                (response, exception) -> {
                  if (exception != null) {
                    return new Response<>(exception.getMessage());
                  } else if (response.hasError()) {
                    return new Response<>(
                        response.getError().getCode() + ": " + response.getError().getMessage());
                  } else {
                    return new Response<>(response.getResult());
                  }
                });
    return SafeFuture.of(responseFuture);
  }

  static class GetPayloadWeb3jResponse extends org.web3j.protocol.core.Response<ExecutionPayload> {}

  static class PreparePayloadWeb3jResponse
      extends org.web3j.protocol.core.Response<PreparePayloadResponse> {}

  static class NewBlockWeb3jResponse
      extends org.web3j.protocol.core.Response<ExecutePayloadResponse> {}

  static class GenericWeb3jResponse extends org.web3j.protocol.core.Response<GenericResponse> {}
}
