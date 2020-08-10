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

import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.time.TimeProvider;

public class ErrorTrackingEth1Provider implements Eth1Provider {

  private final Eth1Provider delegate;
  private final Eth1StatusLogger eth1StatusLogger;

  public ErrorTrackingEth1Provider(
      final Eth1Provider delegate, final AsyncRunner asyncRunner, final TimeProvider timeProvider) {
    this.delegate = delegate;
    eth1StatusLogger = new Eth1StatusLogger(asyncRunner, timeProvider);
  }

  @Override
  public SafeFuture<EthBlock.Block> getEth1Block(final UInt64 blockNumber) {
    return logStatus(delegate.getEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<EthBlock.Block> getEth1Block(final String blockHash) {
    return logStatus(delegate.getEth1Block(blockHash));
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedEth1Block(final String blockHash) {
    return logStatus(delegate.getGuaranteedEth1Block(blockHash));
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedEth1Block(final UInt64 blockNumber) {
    return logStatus(delegate.getGuaranteedEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<EthBlock.Block> getLatestEth1Block() {
    return logStatus(delegate.getLatestEth1Block());
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, final String to, final String data, final UInt64 blockNumber) {
    return logStatus(delegate.ethCall(from, to, data, blockNumber));
  }

  private <T> SafeFuture<T> logStatus(final SafeFuture<T> action) {
    return action
        .thenPeek((t) -> eth1StatusLogger.success())
        .catchAndRethrow(throwable -> eth1StatusLogger.fail());
  }
}
