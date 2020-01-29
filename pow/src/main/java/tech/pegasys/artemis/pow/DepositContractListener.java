/*
 * Copyright 2019 ConsenSys AG.
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

import static tech.pegasys.artemis.pow.contract.DepositContract.DEPOSITEVENT_EVENT;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.exception.Eth1RequestException;
import tech.pegasys.artemis.util.async.SafeFuture;

public class DepositContractListener {
  private final Disposable subscriptionNewDeposit;
  private final Web3j web3j;
  private final DepositContract contract;
  private volatile Optional<EthBlock.Block> cachedBlock = Optional.empty();
  private final PublishOnInactivityDepositHandler depositHandler;

  public DepositContractListener(
      Web3j web3j,
      DepositContract contract,
      final PublishOnInactivityDepositHandler depositHandler) {
    this.web3j = web3j;
    this.contract = contract;
    this.depositHandler = depositHandler;

    // Filter by the contract address and by begin/end blocks
    EthFilter depositEventFilter =
        new EthFilter(
                DefaultBlockParameterName.EARLIEST,
                DefaultBlockParameterName.LATEST,
                contract.getContractAddress().substring(2))
            .addSingleTopic(EventEncoder.encode(DEPOSITEVENT_EVENT));

    // Subscribe to the event of a validator being registered in the
    // DepositContract
    subscriptionNewDeposit =
        contract
            .depositEventEventFlowable(depositEventFilter)
            .flatMap(
                event ->
                    getBlockByHash(event.log.getBlockHash())
                        .map(block -> Pair.of(block, new Deposit(event))))
            .subscribe(pair -> this.depositHandler.onDepositEvent(pair.getLeft(), pair.getRight()));
  }

  private Flowable<Block> getBlockByHash(final String blockHash) {
    return cachedBlock
        .filter(block -> block.getHash().equals(blockHash))
        .map(Flowable::just)
        .orElseGet(
            () ->
                web3j
                    .ethGetBlockByHash(blockHash, false)
                    .flowable()
                    .map(
                        blockResponse -> {
                          cachedBlock = Optional.of(blockResponse.getBlock());
                          return blockResponse.getBlock();
                        }));
  }

  @SuppressWarnings("rawtypes")
  public SafeFuture<Bytes32> getDepositRoot(UnsignedLong blockHeight) {
    String encodedFunction = contract.get_deposit_root().encodeFunctionCall();
    return callFunctionAtBlockNumber(encodedFunction, blockHeight)
        .thenApply(
            value -> {
              List<Type> list = contract.get_deposit_root().decodeFunctionResponse(value);
              return Bytes32.wrap((byte[]) list.get(0).getValue());
            });
  }

  @SuppressWarnings("rawtypes")
  public SafeFuture<UnsignedLong> getDepositCount(UnsignedLong blockHeight) {
    String encodedFunction = contract.get_deposit_count().encodeFunctionCall();
    return callFunctionAtBlockNumber(encodedFunction, blockHeight)
        .thenApply(
            value -> {
              List<Type> list = contract.get_deposit_count().decodeFunctionResponse(value);
              byte[] bytes = (byte[]) list.get(0).getValue();
              long deposit_count = Bytes.wrap(bytes).reverse().toLong();
              return UnsignedLong.valueOf(deposit_count);
            });
  }

  public DepositContract getContract() {
    return contract;
  }

  public void stop() {
    subscriptionNewDeposit.dispose();
  }

  private SafeFuture<String> callFunctionAtBlockNumber(
      String encodedFunction, UnsignedLong blockHeight) {
    return SafeFuture.of(
            web3j
                .ethCall(
                    Transaction.createEthCallTransaction(
                        null, contract.getContractAddress(), encodedFunction),
                    DefaultBlockParameter.valueOf(blockHeight.bigIntegerValue()))
                .sendAsync())
        .thenApply(
            ethCall -> {
              if (ethCall.hasError()) {
                throw new Eth1RequestException(
                    "Eth1 call has failed:" + ethCall.getError().getMessage());
              } else {
                return ethCall.getValue();
              }
            });
  }
}
