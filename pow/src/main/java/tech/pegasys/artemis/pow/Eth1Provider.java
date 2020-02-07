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
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.util.async.SafeFuture;

public class Eth1Provider {

  private final Web3j web3j;

  public Eth1Provider(Web3j web3j) {
    this.web3j = web3j;
  }

  public Flowable<EthBlock.Block> getLatestBlockFlowable() {
    return web3j.blockFlowable(false).map(EthBlock::getBlock);
  }

  public SafeFuture<EthBlock.Block> getEth1BlockFuture(UnsignedLong blockNumber) {
    DefaultBlockParameter blockParameter =
        DefaultBlockParameter.valueOf(blockNumber.bigIntegerValue());
    return getEth1BlockFuture(blockParameter);
  }

  public SafeFuture<EthBlock.Block> getEth1BlockFuture(String blockHash) {
    return SafeFuture.of(web3j.ethGetBlockByHash(blockHash, false).sendAsync())
        .thenApply(EthBlock::getBlock);
  }

  public SafeFuture<EthBlock.Block> getEth1BlockFuture(DefaultBlockParameter blockParameter) {
    return SafeFuture.of(web3j.ethGetBlockByNumber(blockParameter, false).sendAsync())
        .thenApply(EthBlock::getBlock);
  }

  public SafeFuture<EthBlock.Block> getLatestEth1BlockFuture() {
    DefaultBlockParameter blockParameter = DefaultBlockParameterName.LATEST;
    return getEth1BlockFuture(blockParameter);
  }
}
