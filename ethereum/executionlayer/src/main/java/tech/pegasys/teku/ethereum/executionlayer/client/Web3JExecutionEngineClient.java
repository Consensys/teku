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

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

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
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtAuthInterceptor;
import tech.pegasys.teku.ethereum.executionlayer.client.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadHeaderV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.SignedBlindedBeaconBlock;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public class Web3JExecutionEngineClient implements ExecutionEngineClient {
  private static final Logger LOG = LogManager.getLogger();

  private static final int ERROR_REPEAT_DELAY_MILLIS = 30 * 1000;
  private static final int NO_ERROR_TIME = -1;
  private final Web3j eth1Web3j;
  protected final HttpService eeWeb3jService;
  private final TimeProvider timeProvider;
  private final AtomicLong lastError = new AtomicLong(NO_ERROR_TIME);

  public Web3JExecutionEngineClient(
      final String eeEndpoint,
      final TimeProvider timeProvider,
      final Optional<JwtConfig> jwtConfig) {
    this.eeWeb3jService = new HttpService(eeEndpoint, createOkHttpClient(jwtConfig, timeProvider));
    this.eth1Web3j = Web3j.build(eeWeb3jService);
    this.timeProvider = timeProvider;
  }

  private static OkHttpClient createOkHttpClient(
      final Optional<JwtConfig> jwtConfig, final TimeProvider timeProvider) {
    final OkHttpClient.Builder builder = new OkHttpClient.Builder();
    if (LOG.isTraceEnabled()) {
      HttpLoggingInterceptor logging = new HttpLoggingInterceptor(LOG::trace);
      logging.setLevel(HttpLoggingInterceptor.Level.BODY);
      builder.addInterceptor(logging);
    }
    jwtConfig.ifPresent(jwt -> builder.addInterceptor(new JwtAuthInterceptor(jwt, timeProvider)));
    return builder.build();
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(Bytes32 blockHash) {
    return doWeb3JRequest(eth1Web3j.ethGetBlockByHash(blockHash.toHexString(), false).sendAsync())
        .thenApply(EthBlock::getBlock)
        .thenApply(Web3JExecutionEngineClient::eth1BlockToPowBlock)
        .thenApply(Optional::ofNullable);
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return doWeb3JRequest(
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
    Request<?, ExecutionPayloadV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_getPayloadV1",
            Collections.singletonList(payloadId.toHexString()),
            eeWeb3jService,
            ExecutionPayloadV1Web3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<PayloadStatusV1>> newPayload(ExecutionPayloadV1 executionPayload) {
    Request<?, PayloadStatusV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_newPayloadV1",
            Collections.singletonList(executionPayload),
            eeWeb3jService,
            PayloadStatusV1Web3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdated(
      ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes) {
    Request<?, ForkChoiceUpdatedResultWeb3jResponse> web3jRequest =
        new Request<>(
            "engine_forkchoiceUpdatedV1",
            list(forkChoiceState, payloadAttributes.orElse(null)),
            eeWeb3jService,
            ForkChoiceUpdatedResultWeb3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<TransitionConfigurationV1>> exchangeTransitionConfiguration(
      TransitionConfigurationV1 transitionConfiguration) {
    Request<?, TransitionConfigurationV1Web3jResponse> web3jRequest =
        new Request<>(
            "engine_exchangeTransitionConfigurationV1",
            Collections.singletonList(transitionConfiguration),
            eeWeb3jService,
            TransitionConfigurationV1Web3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadHeaderV1>> getPayloadHeader(Bytes8 payloadId) {
    Request<?, ExecutionPayloadHeaderV1Web3jResponse> web3jRequest =
        new Request<>(
            "builder_getPayloadHeaderV1",
            Collections.singletonList(payloadId.toHexString()),
            eeWeb3jService,
            ExecutionPayloadHeaderV1Web3jResponse.class);
    return doRequest(web3jRequest);
  }

  @Override
  public SafeFuture<Response<ExecutionPayloadV1>> proposeBlindedBlock(
      SignedBlindedBeaconBlock signedBlindedBeaconBlock) {
    Request<?, ExecutionPayloadV1Web3jResponse> web3jRequest =
        new Request<>(
            "builder_proposeBlindedBlockV1",
            Collections.singletonList(signedBlindedBeaconBlock),
            eeWeb3jService,
            ExecutionPayloadV1Web3jResponse.class);
    return doRequest(web3jRequest);
  }

  private void handleError(Throwable error) {
    final long errorTime = lastError.get();
    if (errorTime == NO_ERROR_TIME
        || timeProvider.getTimeInMillis().longValue() - errorTime > ERROR_REPEAT_DELAY_MILLIS) {
      if (lastError.compareAndSet(errorTime, timeProvider.getTimeInMillis().longValue())) {
        EVENT_LOG.executionClientIsOffline(error);
      }
    }
  }

  private void handleSuccess() {
    if (lastError.getAndUpdate(x -> NO_ERROR_TIME) != NO_ERROR_TIME) {
      EVENT_LOG.executionClientIsOnline();
    }
  }

  private <T> SafeFuture<T> doWeb3JRequest(CompletableFuture<T> web3Request) {
    return SafeFuture.of(web3Request)
        .catchAndRethrow(this::handleError)
        .thenPeek(__ -> handleSuccess());
  }

  protected <T> SafeFuture<Response<T>> doRequest(
      Request<?, ? extends org.web3j.protocol.core.Response<T>> web3jRequest) {
    CompletableFuture<Response<T>> responseFuture =
        web3jRequest
            .sendAsync()
            .handle(
                (response, exception) -> {
                  if (exception != null) {
                    handleError(exception);
                    return new Response<>(exception.getMessage());
                  } else if (response.hasError()) {
                    final String errorMessage =
                        response.getError().getCode() + ": " + response.getError().getMessage();
                    handleError(new Error(errorMessage));
                    return new Response<>(errorMessage);
                  } else {
                    handleSuccess();
                    return new Response<>(response.getResult());
                  }
                });
    return SafeFuture.of(responseFuture);
  }

  static class ExecutionPayloadV1Web3jResponse
      extends org.web3j.protocol.core.Response<ExecutionPayloadV1> {}

  static class PayloadStatusV1Web3jResponse
      extends org.web3j.protocol.core.Response<PayloadStatusV1> {}

  static class ForkChoiceUpdatedResultWeb3jResponse
      extends org.web3j.protocol.core.Response<ForkChoiceUpdatedResult> {}

  static class TransitionConfigurationV1Web3jResponse
      extends org.web3j.protocol.core.Response<TransitionConfigurationV1> {}

  static class ExecutionPayloadHeaderV1Web3jResponse
      extends org.web3j.protocol.core.Response<ExecutionPayloadHeaderV1> {}

  /**
   * Returns a list that supports null items.
   *
   * @param items the items to put in a list
   * @return the list
   */
  protected List<Object> list(final Object... items) {
    final List<Object> list = new ArrayList<>();
    for (Object item : items) {
      list.add(item);
    }
    return list;
  }
}
