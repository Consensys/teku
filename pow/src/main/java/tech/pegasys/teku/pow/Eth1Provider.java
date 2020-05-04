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
import io.reactivex.Flowable;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.teku.util.async.SafeFuture;

public interface Eth1Provider {

  Flowable<Block> getLatestBlockFlowable();

  SafeFuture<Block> getEth1BlockFuture(UnsignedLong blockNumber);

  SafeFuture<Block> getEth1BlockFuture(String blockHash);

  SafeFuture<Block> getGuaranteedEth1BlockFuture(String blockHash);

  SafeFuture<Block> getGuaranteedEth1BlockFuture(UnsignedLong blockNumber);

  SafeFuture<Block> getLatestEth1BlockFuture();

  SafeFuture<EthCall> ethCall(String from, String to, String data, UnsignedLong blockNumber);
}
