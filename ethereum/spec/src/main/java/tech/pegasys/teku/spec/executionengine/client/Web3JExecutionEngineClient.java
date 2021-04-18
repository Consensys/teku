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

package tech.pegasys.teku.spec.executionengine.client;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.executionengine.client.schema.AssembleBlockRequest;
import tech.pegasys.teku.spec.executionengine.client.schema.ExecutionPayload;
import tech.pegasys.teku.spec.executionengine.client.schema.GenericResponse;
import tech.pegasys.teku.spec.executionengine.client.schema.NewBlockResponse;
import tech.pegasys.teku.spec.executionengine.client.schema.Response;

public class Web3JExecutionEngineClient implements ExecutionEngineClient {

  private final HttpService web3jService;

  public Web3JExecutionEngineClient(String eth1Endpoint) {
    this.web3jService = new HttpService(eth1Endpoint);
  }

  @Override
  public SafeFuture<Response<ExecutionPayload>> consensusAssembleBlock(
      AssembleBlockRequest request) {
    Request<?, AssembleBlockWeb3jResponse> web3jRequest =
        new Request<>(
            "consensus_assembleBlock",
            Collections.singletonList(request),
            web3jService,
            AssembleBlockWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<NewBlockResponse>> consensusNewBlock(ExecutionPayload request) {
    Request<?, NewBlockWeb3jResponse> web3jRequest =
        new Request<>(
            "consensus_newBlock",
            Collections.singletonList(request),
            web3jService,
            NewBlockWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<GenericResponse>> consensusSetHead(Bytes32 blockHash) {
    Request<?, GenericWeb3jResponse> web3jRequest =
        new Request<>(
            "consensus_setHead",
            Collections.singletonList(blockHash.toHexString()),
            web3jService,
            GenericWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<GenericResponse>> consensusFinalizeBlock(Bytes32 blockHash) {
    Request<?, GenericWeb3jResponse> web3jRequest =
        new Request<>(
            "consensus_setHead",
            Collections.singletonList(blockHash.toHexString()),
            web3jService,
            GenericWeb3jResponse.class);
    return doRequest(web3jRequest);
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

  static class AssembleBlockWeb3jResponse
      extends org.web3j.protocol.core.Response<ExecutionPayload> {}

  static class NewBlockWeb3jResponse extends org.web3j.protocol.core.Response<NewBlockResponse> {}

  static class GenericWeb3jResponse extends org.web3j.protocol.core.Response<GenericResponse> {}
}
