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

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.reactivex.disposables.Disposable;
import java.math.BigInteger;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.exception.DepositCountRequestException;
import tech.pegasys.artemis.pow.exception.DepositRootRequestException;
import tech.pegasys.artemis.util.async.SafeFuture;

public class DepositContractListener {

  private final Disposable subscriptionNewDeposit;
  private final Web3j web3j;
  private final DepositContract contract;

  public DepositContractListener(Web3j web3j, EventBus eventBus, DepositContract contract) {
    this.web3j = web3j;
    this.contract = contract;

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
            .subscribe(
                response -> {
                  Deposit deposit = new Deposit(response);
                  eventBus.post(deposit);
                });
  }

  @SuppressWarnings("rawtypes")
  public SafeFuture<Bytes32> getDepositRoot(BigInteger blockHeight) {
    String encodedFunction = contract.get_deposit_root().encodeFunctionCall();
    return callFunctionAtBlockHeight(encodedFunction, blockHeight)
        .thenApply(
            ethCall -> {
              if (ethCall.hasError()) {
                throw new DepositRootRequestException(
                    "Eth1 call get_deposit_root() has failed:" + ethCall.getError().getMessage());
              }
              String value = ethCall.getValue();
              List<Type> list = contract.get_deposit_root().decodeFunctionResponse(value);
              return Bytes32.wrap((byte[]) list.get(0).getValue());
            });
  }

  @SuppressWarnings("rawtypes")
  public SafeFuture<UnsignedLong> getDepositCount(BigInteger blockHeight) {
    String encodedFunction = contract.get_deposit_count().encodeFunctionCall();
    return callFunctionAtBlockHeight(encodedFunction, blockHeight)
        .thenApply(
            ethCall -> {
              if (ethCall.hasError()) {
                throw new DepositCountRequestException(
                    "Eth1 call get_deposit_count() has failed:" + ethCall.getError().getMessage());
              }
              String value = ethCall.getValue();
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

  private SafeFuture<EthCall> callFunctionAtBlockHeight(
      String encodedFunction, BigInteger blockHeight) {
    return SafeFuture.of(
        web3j
            .ethCall(
                Transaction.createEthCallTransaction(
                    null, contract.getContractAddress(), encodedFunction),
                DefaultBlockParameter.valueOf(blockHeight))
            .sendAsync());
  }
}
