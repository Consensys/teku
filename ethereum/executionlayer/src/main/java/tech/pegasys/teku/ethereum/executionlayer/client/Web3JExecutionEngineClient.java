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

package tech.pegasys.teku.ethereum.executionlayer.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutePayloadResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public class Web3JExecutionEngineClient implements ExecutionEngineClient {
  private static final Logger LOG = LogManager.getLogger();

  private final Web3j eth1Web3j;
  private final HttpService eeWeb3jService;
  private final AtomicLong nextId = new AtomicLong(MESSAGE_ORDER_RESET_ID);

  public Web3JExecutionEngineClient(String eeEndpoint) {
    this.eeWeb3jService = new HttpService(eeEndpoint, createOkHttpClient());
    this.eth1Web3j = Web3j.build(eeWeb3jService);
  }

  private static OkHttpClient createOkHttpClient() {
    final OkHttpClient.Builder builder = new OkHttpClient.Builder();
    if (LOG.isTraceEnabled()) {
      HttpLoggingInterceptor logging = new HttpLoggingInterceptor(LOG::trace);
      logging.setLevel(HttpLoggingInterceptor.Level.BODY);
      builder.addInterceptor(logging);
    }
    return builder.build();
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(Bytes32 blockHash) {
    return SafeFuture.of(eth1Web3j.ethGetBlockByHash(blockHash.toHexString(), false).sendAsync())
        .thenApply(EthBlock::getBlock)
        .thenApply(Web3JExecutionEngineClient::eth1BlockToPowBlock)
        .thenApply(Optional::ofNullable);
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return SafeFuture.of(
            eth1Web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).sendAsync())
        .thenApply(EthBlock::getBlock)
        .thenApply(Web3JExecutionEngineClient::eth1BlockToPowBlock);
  }

  private static PowBlock eth1BlockToPowBlock(EthBlock.Block eth1Block) {
    return eth1Block == null
        ? null
        : new PowBlock(
            Bytes32.fromHexStringStrict(eth1Block.getHash()),
            Bytes32.fromHexStringStrict(eth1Block.getParentHash()),
            UInt256.valueOf(eth1Block.getTotalDifficulty()));
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> getPayload(Bytes8 payloadId) {
    Request<?, GetPayloadWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_getPayloadV1",
            Collections.singletonList(payloadId.toHexString()),
            eeWeb3jService,
            GetPayloadWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ExecutePayloadResult>> executePayload(
      ExecutionPayloadV1 executionPayload) {
    Request<?, ExecutePayloadWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_executePayloadV1",
            Collections.singletonList(executionPayload),
            eeWeb3jService,
            ExecutePayloadWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdated(
      ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes) {
    Request<?, ForkChoiceUpdatedWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_forkchoiceUpdatedV1",
            list(forkChoiceState, payloadAttributes.orElse(null)),
            eeWeb3jService,
            ForkChoiceUpdatedWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  private <T> SafeFuture<Response<T>> doRequest(
      Request<?, ? extends org.web3j.protocol.core.Response<T>> web3jRequest) {
    web3jRequest.setId(nextId.getAndIncrement());
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

  static class GetPayloadWeb3jResponse
      extends org.web3j.protocol.core.Response<ExecutionPayloadV1> {}

  static class ExecutePayloadWeb3jResponse
      extends org.web3j.protocol.core.Response<ExecutePayloadResult> {}

  static class ForkChoiceUpdatedWeb3jResponse
      extends org.web3j.protocol.core.Response<ForkChoiceUpdatedResult> {}

  /**
   * Returns a list that supports null items.
   *
   * @param items the items to put in a list
   * @return the list
   */
  private List<Object> list(final Object... items) {
    final List<Object> list = new ArrayList<>();
    for (Object item : items) {
      list.add(item);
    }
    return list;
  }
}
