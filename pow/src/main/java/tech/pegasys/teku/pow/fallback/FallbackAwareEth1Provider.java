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

package tech.pegasys.teku.pow.fallback;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.Eth1Provider;
import tech.pegasys.teku.pow.fallback.strategy.Eth1ProviderSelector;

public class FallbackAwareEth1Provider implements Eth1Provider {
  private static final Logger LOG = LogManager.getLogger();
  private final Eth1ProviderSelector eth1ProviderSelector;

  public FallbackAwareEth1Provider(final Eth1ProviderSelector eth1ProviderSelector) {
    this.eth1ProviderSelector = eth1ProviderSelector;
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1Block(final UInt64 blockNumber) {
    return run(eth1Provider -> eth1Provider.getEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      final UInt64 blockNumber, final Duration retryDelay, final int maxRetries) {
    return run(
        eth1Provider -> eth1Provider.getEth1BlockWithRetry(blockNumber, retryDelay, maxRetries));
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final String blockHash) {
    return run(eth1Provider -> eth1Provider.getGuaranteedEth1Block(blockHash));
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final UInt64 blockNumber) {
    return run(eth1Provider -> eth1Provider.getGuaranteedEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1Block(final String blockHash) {
    return run(eth1Provider -> eth1Provider.getEth1Block(blockHash));
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      final String blockHash, final Duration retryDelay, final int maxRetries) {
    return run(
        eth1Provider -> eth1Provider.getEth1BlockWithRetry(blockHash, retryDelay, maxRetries));
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

  private <T> SafeFuture<T> run(final Function<Eth1Provider, SafeFuture<T>> task) {
    return run(task, eth1ProviderSelector.candidateCount());
  }

  private <T> SafeFuture<T> run(
      final Function<Eth1Provider, SafeFuture<T>> task, final int maxRetries) {
    return SafeFuture.of(
        () ->
            task.apply(eth1ProviderSelector.bestCandidate())
                .exceptionallyCompose(
                    err -> {
                      LOG.warn(
                          "Error caught while calling Eth1Provider, switching provider for next calls",
                          err);
                      eth1ProviderSelector.updateBestCandidate();
                      if (maxRetries > 0) {
                        return run(task, maxRetries - 1);
                      } else {
                        return SafeFuture.failedFuture(err);
                      }
                    }));
  }
}
