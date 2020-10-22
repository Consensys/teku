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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ThrottlingEth1Provider implements Eth1Provider {
  private final Eth1Provider delegate;
  private final int maximumConcurrentRequests;
  private final Queue<Runnable> queuedRequests = new ConcurrentLinkedQueue<>();
  private int inflightRequestCount = 0;

  public ThrottlingEth1Provider(final Eth1Provider delegate, final int maximumConcurrentRequests) {
    this.delegate = delegate;
    this.maximumConcurrentRequests = maximumConcurrentRequests;
  }

  @Override
  public SafeFuture<Block> getEth1Block(final UInt64 blockNumber) {
    return queueRequest(() -> delegate.getEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final String blockHash) {
    return queueRequest(() -> delegate.getGuaranteedEth1Block(blockHash));
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1Block(final UInt64 blockNumber) {
    return queueRequest(() -> delegate.getGuaranteedEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<Block> getEth1Block(final String blockHash) {
    return queueRequest(() -> delegate.getEth1Block(blockHash));
  }

  @Override
  public SafeFuture<Block> getLatestEth1Block() {
    return queueRequest(delegate::getLatestEth1Block);
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, final String to, final String data, final UInt64 blockNumber) {
    return queueRequest(() -> delegate.ethCall(from, to, data, blockNumber));
  }

  @Override
  public SafeFuture<BigInteger> getChainId() {
    return queueRequest(delegate::getChainId);
  }

  private <T> SafeFuture<T> queueRequest(final Supplier<SafeFuture<T>> request) {
    final SafeFuture<T> future = new SafeFuture<>();
    queuedRequests.add(
        () -> {
          final SafeFuture<T> requestFuture = request.get();
          requestFuture.propagateTo(future);
          requestFuture.always(this::requestComplete);
        });
    processQueuedRequests();
    return future;
  }

  private synchronized void requestComplete() {
    inflightRequestCount--;
    processQueuedRequests();
  }

  private synchronized void processQueuedRequests() {
    while (inflightRequestCount < maximumConcurrentRequests && !queuedRequests.isEmpty()) {
      inflightRequestCount++;
      queuedRequests.remove().run();
    }
  }
}
