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
import java.util.List;
import java.util.Optional;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.Eth1Provider;
import tech.pegasys.teku.pow.fallback.readiness.Eth1ProviderChainHeadReadiness;
import tech.pegasys.teku.pow.fallback.strategy.Eth1ProviderSelector;
import tech.pegasys.teku.pow.fallback.strategy.RoundRobinEth1ProviderSelector;

public class FallbackAwareEth1Provider implements Eth1Provider {
  private final Eth1ProviderSelector eth1ProviderSelector;

  public FallbackAwareEth1Provider(final List<Eth1Provider> delegates) {
    // TODO change providerReadinessCheckIntervalSeconds to a positive value
    this.eth1ProviderSelector =
        new RoundRobinEth1ProviderSelector(
            delegates, new Eth1ProviderChainHeadReadiness(delegates), -1);
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1Block(final UInt64 blockNumber) {
    return eth1ProviderSelector.bestCandidate().getEth1Block(blockNumber);
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      final UInt64 blockNumber, final Duration retryDelay, final int maxRetries) {
    return eth1ProviderSelector
        .bestCandidate()
        .getEth1BlockWithRetry(blockNumber, retryDelay, maxRetries);
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final String blockHash) {
    return eth1ProviderSelector.bestCandidate().getGuaranteedEth1Block(blockHash);
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final UInt64 blockNumber) {
    return eth1ProviderSelector.bestCandidate().getGuaranteedEth1Block(blockNumber);
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1Block(final String blockHash) {
    return eth1ProviderSelector.bestCandidate().getEth1Block(blockHash);
  }

  @Override
  public SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      final String blockHash, final Duration retryDelay, final int maxRetries) {
    return eth1ProviderSelector
        .bestCandidate()
        .getEth1BlockWithRetry(blockHash, retryDelay, maxRetries);
  }

  @Override
  public SafeFuture<Block> getLatestEth1Block() {
    return eth1ProviderSelector.bestCandidate().getLatestEth1Block();
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, final String to, final String data, final UInt64 blockNumber) {
    return eth1ProviderSelector.bestCandidate().ethCall(from, to, data, blockNumber);
  }

  @Override
  public SafeFuture<BigInteger> getChainId() {
    return eth1ProviderSelector.bestCandidate().getChainId();
  }
}
