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
import java.time.Duration;
import java.util.Optional;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface Eth1Provider {

  SafeFuture<Optional<Block>> getEth1Block(UInt64 blockNumber);

  SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      UInt64 blockNumber, Duration retryDelay, int maxRetries);

  default SafeFuture<Optional<Block>> getEth1BlockWithRetry(UInt64 blockNumber) {
    return getEth1BlockWithRetry(blockNumber, Duration.ofSeconds(5), 2);
  }

  SafeFuture<Optional<Block>> getEth1Block(String blockHash);

  SafeFuture<Optional<Block>> getEth1BlockWithRetry(
      String blockHash, Duration retryDelay, int maxRetries);

  default SafeFuture<Optional<Block>> getEth1BlockWithRetry(String blockHash) {
    return getEth1BlockWithRetry(blockHash, Duration.ofSeconds(5), 2);
  }

  SafeFuture<Block> getGuaranteedEth1Block(String blockHash);

  SafeFuture<Block> getGuaranteedEth1Block(UInt64 blockNumber);

  SafeFuture<Block> getLatestEth1Block();

  SafeFuture<EthCall> ethCall(String from, String to, String data, UInt64 blockNumber);

  SafeFuture<BigInteger> getChainId();
}
