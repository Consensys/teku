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

package tech.pegasys.teku.pow;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthChainId;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class Web3jEth1Provider implements Eth1Provider {
  private static final Logger LOG = LogManager.getLogger();

  private final Web3j web3j;
  private final AsyncRunner asyncRunner;

  public Web3jEth1Provider(final Web3j web3j, final AsyncRunner asyncRunner) {
    this.web3j = web3j;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<Optional<EthBlock.Block>> getEth1Block(final UInt64 blockNumber) {
    LOG.trace("Getting eth1 block {}", blockNumber);
    DefaultBlockParameter blockParameter =
        DefaultBlockParameter.valueOf(blockNumber.bigIntegerValue());
    return getEth1Block(blockParameter).thenApply(Optional::ofNullable);
  }

  @Override
  public SafeFuture<Optional<EthBlock.Block>> getEth1BlockWithRetry(
      final UInt64 blockNumber, final Duration retryDelay, final int maxRetries) {
    return asyncRunner.runWithRetry(() -> getEth1Block(blockNumber), retryDelay, maxRetries);
  }

  @Override
  public SafeFuture<Optional<EthBlock.Block>> getEth1Block(final String blockHash) {
    LOG.trace("Getting eth1 block {}", blockHash);
    return sendAsync(web3j.ethGetBlockByHash(blockHash, false))
        .thenApply(EthBlock::getBlock)
        .thenApply(Optional::ofNullable);
  }

  @Override
  public SafeFuture<Optional<EthBlock.Block>> getEth1BlockWithRetry(
      final String blockHash, final Duration retryDelay, final int maxRetries) {
    return asyncRunner.runWithRetry(() -> getEth1Block(blockHash), retryDelay, maxRetries);
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedEth1Block(final String blockHash) {
    return getEth1Block(blockHash)
        .thenApply(Optional::get)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug("Retrying Eth1 request for block: {}", blockHash, err);
              return asyncRunner
                  .getDelayedFuture(
                      Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT, TimeUnit.MILLISECONDS)
                  .thenCompose(__ -> getGuaranteedEth1Block(blockHash));
            });
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedEth1Block(final UInt64 blockNumber) {
    return getEth1Block(blockNumber)
        .thenApply(Optional::get)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug("Retrying Eth1 request for block: {}", blockNumber, err);
              return asyncRunner
                  .getDelayedFuture(
                      Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT, TimeUnit.MILLISECONDS)
                  .thenCompose(__ -> getGuaranteedEth1Block(blockNumber));
            });
  }

  private SafeFuture<EthBlock.Block> getEth1Block(final DefaultBlockParameter blockParameter) {
    return sendAsync(web3j.ethGetBlockByNumber(blockParameter, false))
        .thenApply(EthBlock::getBlock);
  }

  @SuppressWarnings("rawtypes")
  private <S, T extends Response> SafeFuture<T> sendAsync(final Request<S, T> request) {
    try {
      return SafeFuture.of(request.sendAsync());
    } catch (RejectedExecutionException ex) {
      LOG.debug("shutting down, ignoring error", ex);
      return new SafeFuture<>();
    }
  }

  @Override
  public SafeFuture<EthBlock.Block> getLatestEth1Block() {
    DefaultBlockParameter blockParameter = DefaultBlockParameterName.LATEST;
    return getEth1Block(blockParameter);
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, String to, String data, final UInt64 blockNumber) {
    return SafeFuture.of(
        web3j
            .ethCall(
                Transaction.createEthCallTransaction(from, to, data),
                DefaultBlockParameter.valueOf(blockNumber.bigIntegerValue()))
            .sendAsync());
  }

  @Override
  public SafeFuture<BigInteger> getChainId() {
    return sendAsync(web3j.ethChainId()).thenApply(EthChainId::getChainId);
  }
}
