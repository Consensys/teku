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

import com.google.common.primitives.UnsignedLong;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.teku.util.async.SafeFuture;

public class ErrorTrackingEth1Provider implements Eth1Provider {

  private final Eth1Provider delegate;
  private final Eth1StatusLogger eth1StatusLogger;

  public ErrorTrackingEth1Provider(
      final Eth1Provider delegate, final Eth1StatusLogger eth1StatusLogger) {
    this.delegate = delegate;
    this.eth1StatusLogger = eth1StatusLogger;
  }

  @Override
  public SafeFuture<EthBlock.Block> getEth1Block(final UnsignedLong blockNumber) {
    return logSuccess(delegate.getEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<EthBlock.Block> getEth1Block(final String blockHash) {
    return logSuccess(delegate.getEth1Block(blockHash));
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedEth1Block(final String blockHash) {
    return logSuccess(delegate.getGuaranteedEth1Block(blockHash));
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedEth1Block(final UnsignedLong blockNumber) {
    return logSuccess(delegate.getGuaranteedEth1Block(blockNumber));
  }

  @Override
  public SafeFuture<EthBlock.Block> getLatestEth1Block() {
    return logSuccess(delegate.getLatestEth1Block());
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, final String to, final String data, final UnsignedLong blockNumber) {
    return logSuccess(delegate.ethCall(from, to, data, blockNumber));
  }

  private <T> SafeFuture<T> logSuccess(SafeFuture<T> action) {
    return action.thenPeek((t) -> eth1StatusLogger.success());
  }
}
