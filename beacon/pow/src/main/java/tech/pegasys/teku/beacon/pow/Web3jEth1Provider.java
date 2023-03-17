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

package tech.pegasys.teku.beacon.pow;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.google.common.base.Throwables;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.Response.Error;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.core.methods.response.EthLog;
import tech.pegasys.teku.beacon.pow.exception.RejectedRequestException;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.config.SpecConfig;

public class Web3jEth1Provider extends AbstractMonitorableEth1Provider {
  private static final Logger LOG = LogManager.getLogger();

  private final AtomicBoolean validating = new AtomicBoolean(false);

  private final String id;
  private final SpecConfig config;
  private final Web3j web3j;
  private final AsyncRunner asyncRunner;
  private final LabelledMetric<Counter> requestCounter;

  public Web3jEth1Provider(
      final SpecConfig config,
      final MetricsSystem metricsSystem,
      final String id,
      final Web3j web3j,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider) {
    super(timeProvider);
    this.config = config;
    this.web3j = web3j;
    this.asyncRunner = asyncRunner;
    this.id = id;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "eth1_requests_total",
            "Counter of the number of requests made to the eth1-endpoint, by endpoint ID and JSON-RPC method",
            "endpoint",
            "method");
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
                  .getDelayedFuture(Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT)
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
                  .getDelayedFuture(Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT)
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
      requestCounter.labels(id, request.getMethod()).inc();
      return SafeFuture.of(request.sendAsync())
          .thenApply(
              response -> {
                if (response.hasError()) {
                  final Error error = response.getError();
                  throw new RejectedRequestException(error.getCode(), error.getMessage());
                } else {
                  updateLastCall(Result.SUCCESS);
                  return response;
                }
              })
          .catchAndRethrow(__ -> updateLastCall(Result.FAILED));
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
  public SafeFuture<EthBlock.Block> getGuaranteedLatestEth1Block() {
    return getLatestEth1Block()
        .exceptionallyCompose(
            (err) -> {
              LOG.debug("Retrying Eth1 request for latest block", err);
              return asyncRunner
                  .getDelayedFuture(Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT)
                  .thenCompose(__ -> getGuaranteedLatestEth1Block());
            });
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

  @Override
  public SafeFuture<Boolean> ethSyncing() {
    return sendAsync(web3j.ethSyncing())
        .thenApply(response -> Web3jInSyncCheck.isSyncing(id, response));
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public SafeFuture<List<EthLog.LogResult<?>>> ethGetLogs(EthFilter ethFilter) {
    return sendAsync(web3j.ethGetLogs(ethFilter))
        .thenApply(EthLog::getLogs)
        .thenApply(logs -> (List<EthLog.LogResult<?>>) (List) logs);
  }

  @Override
  public SafeFuture<Boolean> validate() {
    if (validating.compareAndSet(false, true)) {
      LOG.debug("Validating endpoint {} ...", this.id);
      return validateChainId()
          .thenCompose(
              result -> {
                if (result == Result.FAILED) {
                  return SafeFuture.completedFuture(result);
                }
                return validateSyncing();
              })
          .thenApply(
              result -> {
                updateLastValidation(result);
                return result == Result.SUCCESS;
              })
          .exceptionally(
              error -> {
                LOG.warn(
                    "Endpoint {} encountered an issue | {}",
                    this.id,
                    Throwables.getRootCause(error).getMessage());
                updateLastValidation(Result.FAILED);
                return false;
              })
          .alwaysRun(() -> validating.set(false));
    } else {
      LOG.debug("Already validating");
      return SafeFuture.completedFuture(isValid());
    }
  }

  private SafeFuture<Result> validateChainId() {
    return getChainId()
        .handle(
            (chainId, ex) -> {
              if (ex != null) {
                LOG.trace(
                    "eth_chainId method is not supported by provider, skipping validation", ex);
                return Result.NOT_SUPPORTED;
              }
              if (chainId.longValue() != config.getDepositChainId()) {
                STATUS_LOG.eth1DepositChainIdMismatch(
                    config.getDepositChainId(), chainId.longValue(), this.id);
                return Result.FAILED;
              }
              return Result.SUCCESS;
            });
  }

  private SafeFuture<Result> validateSyncing() {
    return ethSyncing()
        .thenApply(
            syncing -> {
              if (syncing) {
                LOG.debug("Endpoint {} failed validation | Still syncing", this.id);
                updateLastValidation(Result.FAILED);
                return Result.FAILED;
              } else {
                LOG.debug("Endpoint {} is valid", this.id);
                updateLastValidation(Result.SUCCESS);
                return Result.SUCCESS;
              }
            });
  }
}
