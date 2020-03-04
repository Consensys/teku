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

package tech.pegasys.artemis.pow;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.Flowable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

import java.util.concurrent.TimeUnit;

public class Web3jEth1Provider implements Eth1Provider {
  private static final Logger LOG = LogManager.getLogger();

  private final Web3j web3j;

  public Web3jEth1Provider(Web3j web3j) {
    this.web3j = web3j;
  }

  @Override
  public Flowable<EthBlock.Block> getLatestBlockFlowable() {
    LOG.trace("Subscribing to new block events");
    return web3j.blockFlowable(false).map(EthBlock::getBlock);
  }

  @Override
  public SafeFuture<EthBlock.Block> getEth1BlockFuture(UnsignedLong blockNumber) {
    LOG.trace("Getting eth1 block {}", blockNumber);
    DefaultBlockParameter blockParameter =
        DefaultBlockParameter.valueOf(blockNumber.bigIntegerValue());
    return getEth1BlockFuture(blockParameter);
  }

  @Override
  public SafeFuture<EthBlock.Block> getEth1BlockFuture(String blockHash) {
    LOG.trace("Getting eth1 block {}", blockHash);
    return SafeFuture.of(web3j.ethGetBlockByHash(blockHash, false).sendAsync())
        .thenApply(EthBlock::getBlock);
  }

  @Override
  public SafeFuture<EthBlock.Block> getGuaranteedEth1BlockFuture(String blockHash, AsyncRunner asyncRunner) {
    return getEth1BlockFuture(blockHash)
            .exceptionallyCompose((err) -> {
              LOG.warn("Retrying Eth1 request for block: {}", blockHash);
              return asyncRunner
                      .getDelayedFuture(Constants.ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT, TimeUnit.MILLISECONDS)
                      .thenCompose(__ -> getGuaranteedEth1BlockFuture(blockHash, asyncRunner));
            });
  }

  private SafeFuture<EthBlock.Block> getEth1BlockFuture(DefaultBlockParameter blockParameter) {
    return SafeFuture.of(web3j.ethGetBlockByNumber(blockParameter, false).sendAsync())
        .thenApply(EthBlock::getBlock);
  }

  @Override
  public SafeFuture<EthBlock.Block> getLatestEth1BlockFuture() {
    DefaultBlockParameter blockParameter = DefaultBlockParameterName.LATEST;
    return getEth1BlockFuture(blockParameter);
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, String to, String data, final UnsignedLong blockNumber) {
    return SafeFuture.of(
        web3j
            .ethCall(
                Transaction.createEthCallTransaction(from, to, data),
                DefaultBlockParameter.valueOf(blockNumber.bigIntegerValue()))
            .sendAsync());
  }
}
