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
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthLog;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.exception.Eth1RequestException;
import tech.pegasys.teku.util.config.Constants;

public class FallbackAwareEth1Provider implements Eth1Provider {
  private static final Logger LOG = LogManager.getLogger();
  private final Eth1ProviderSelector eth1ProviderSelector;
  private final AsyncRunner asyncRunner;

  public FallbackAwareEth1Provider(
      final Eth1ProviderSelector eth1ProviderSelector, final AsyncRunner asyncRunner) {
    this.eth1ProviderSelector = eth1ProviderSelector;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1Block(final UInt64 blockNumber) {
    return run(eth1Provider -> eth1Provider.getEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      final UInt64 blockNumber, final Duration retryDelay, final int maxRetries) {

    return asyncRunner.runWithRetry(
        () -> run(eth1Provider -> eth1Provider.getEth1Block(blockNumber)), retryDelay, maxRetries);
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final String blockHash) {
    return run(eth1Provider -> eth1Provider.getEth1Block(blockHash))
        .thenApply(Optional::get)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug("Retrying Eth1 request for block: {}", blockHash, err);
              return asyncRunner
                  .getDelayedFuture(Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT)
                  .thenCompose(__ -> getGuaranteedEth1Block(blockHash));
            });
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedLatestEth1Block() {
    return run(Eth1Provider::getLatestEth1Block)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug("Retrying Eth1 request for latest block", err);
              return asyncRunner
                  .getDelayedFuture(Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT)
                  .thenCompose(__ -> getGuaranteedLatestEth1Block());
            });
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final UInt64 blockNumber) {
    return run(eth1Provider -> eth1Provider.getEth1Block(blockNumber))
        .thenApply(Optional::get)
        .exceptionallyCompose(
            (err) -> {
              LOG.debug("Retrying Eth1 request for block: {}", blockNumber, err);
              return asyncRunner
                  .getDelayedFuture(Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT)
                  .thenCompose(__ -> getGuaranteedEth1Block(blockNumber));
            });
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1Block(final String blockHash) {
    return run(eth1Provider -> eth1Provider.getEth1Block(blockHash));
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      final String blockHash, final Duration retryDelay, final int maxRetries) {

    return asyncRunner.runWithRetry(
        () -> run(eth1Provider -> eth1Provider.getEth1Block(blockHash)), retryDelay, maxRetries);
  }

  @Override
  public SafeFuture<Block> getLatestEth1Block() {
    return run(Eth1Provider::getLatestEth1Block);
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, final String to, final String data, final UInt64 blockNumber) {
    return run(eth1Provider -> eth1Provider.ethCall(from, to, data, blockNumber));
  }

  @Override
  public SafeFuture<BigInteger> getChainId() {
    return run(Eth1Provider::getChainId);
  }

  @Override
  public SafeFuture<Boolean> ethSyncing() {
    return run(Eth1Provider::ethSyncing);
  }

  @Override
  public SafeFuture<List<EthLog.LogResult<?>>> ethGetLogs(EthFilter ethFilter) {
    return run(eth1Provider -> eth1Provider.ethGetLogs(ethFilter));
  }

  private <T> SafeFuture<T> run(final Function<MonitorableEth1Provider, SafeFuture<T>> task) {
    return run(task, eth1ProviderSelector.getValidProviderIterator(), new Eth1RequestException());
  }

  private <T> SafeFuture<T> run(
      final Function<MonitorableEth1Provider, SafeFuture<T>> task,
      final Eth1ProviderSelector.ValidEth1ProviderIterator providers,
      Eth1RequestException exceptionsContainer) {

    Throwable[] previousExceptions = exceptionsContainer.getSuppressed();
    return providers
        .next()
        .handleComposed(
            (nextAvailableProvider, err) -> {
              if (err != null) {
                LOG.debug(
                    previousExceptions.length > 0
                        ? "All available eth1 endpoints failed"
                        : "No available eth1 endpoints");
                throw exceptionsContainer;
              }
              return task.apply(nextAvailableProvider)
                  .exceptionallyCompose(
                      taskErr -> {
                        LOG.warn("Retrying with next eth1 endpoint", taskErr);
                        exceptionsContainer.addSuppressed(taskErr);
                        return run(task, providers, exceptionsContainer);
                      });
            });
  }
}
