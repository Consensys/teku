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

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthLog;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueue;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ThrottlingEth1Provider implements Eth1Provider {
  private final Eth1Provider delegate;
  private final ThrottlingTaskQueue taskQueue;

  public ThrottlingEth1Provider(
      final Eth1Provider delegate,
      final int maximumConcurrentRequests,
      final MetricsSystem metricsSystem) {
    this.delegate = delegate;
    taskQueue =
        ThrottlingTaskQueue.create(
            maximumConcurrentRequests,
            metricsSystem,
            TekuMetricCategory.BEACON,
            "eth1_request_queue_size");
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1Block(final UInt64 blockNumber) {
    return taskQueue.queueTask(() -> delegate.getEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      final UInt64 blockNumber, final Duration retryDelay, final int maxRetries) {
    return taskQueue.queueTask(
        () -> delegate.getEth1BlockWithRetry(blockNumber, retryDelay, maxRetries));
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final String blockHash) {
    return taskQueue.queueTask(() -> delegate.getGuaranteedEth1Block(blockHash));
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final UInt64 blockNumber) {
    return taskQueue.queueTask(() -> delegate.getGuaranteedEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedLatestEth1Block() {
    return taskQueue.queueTask(delegate::getGuaranteedLatestEth1Block);
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1Block(final String blockHash) {
    return taskQueue.queueTask(() -> delegate.getEth1Block(blockHash));
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      final String blockHash, final Duration retryDelay, final int maxRetries) {
    return taskQueue.queueTask(
        () -> delegate.getEth1BlockWithRetry(blockHash, retryDelay, maxRetries));
  }

  @Override
  public SafeFuture<Block> getLatestEth1Block() {
    return taskQueue.queueTask(delegate::getLatestEth1Block);
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, final String to, final String data, final UInt64 blockNumber) {
    return taskQueue.queueTask(() -> delegate.ethCall(from, to, data, blockNumber));
  }

  @Override
  public SafeFuture<BigInteger> getChainId() {
    return taskQueue.queueTask(delegate::getChainId);
  }

  @Override
  public SafeFuture<Boolean> ethSyncing() {
    return taskQueue.queueTask(delegate::ethSyncing);
  }

  @Override
  public SafeFuture<List<EthLog.LogResult<?>>> ethGetLogs(EthFilter ethFilter) {
    return taskQueue.queueTask(() -> delegate.ethGetLogs(ethFilter));
  }
}
