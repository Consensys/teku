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

package tech.pegasys.teku.pow;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.Web3j;
import org.web3j.tx.ClientTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.contract.DepositContract;
import tech.pegasys.teku.pow.exception.Eth1RequestException;

public class DepositContractAccessor {
  private final Eth1Provider eth1Provider;
  private final DepositContract contract;

  private DepositContractAccessor(Eth1Provider eth1Provider, DepositContract contract) {
    this.eth1Provider = eth1Provider;
    this.contract = contract;
  }

  public static DepositContractAccessor create(
      Eth1Provider eth1Provider, Web3j web3j, String address) {

    DepositContract contract =
        DepositContract.load(
            address, web3j, new ClientTransactionManager(web3j, address), new DefaultGasProvider());

    return new DepositContractAccessor(eth1Provider, contract);
  }

  @SuppressWarnings("rawtypes")
  public SafeFuture<Optional<Bytes32>> getDepositRoot(UInt64 blockHeight) {
    String encodedFunction = contract.get_deposit_root().encodeFunctionCall();
    return callFunctionAtBlockNumber(encodedFunction, blockHeight)
        .thenApply(
            value -> {
              List<Type> list = contract.get_deposit_root().decodeFunctionResponse(value);
              if (list.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(Bytes32.wrap((byte[]) list.get(0).getValue()));
            });
  }

  @SuppressWarnings("rawtypes")
  public SafeFuture<Optional<UInt64>> getDepositCount(UInt64 blockHeight) {
    String encodedFunction = contract.get_deposit_count().encodeFunctionCall();
    return callFunctionAtBlockNumber(encodedFunction, blockHeight)
        .thenApply(
            value -> {
              List<Type> list = contract.get_deposit_count().decodeFunctionResponse(value);
              if (list.isEmpty()) {
                return Optional.empty();
              }
              byte[] bytes = (byte[]) list.get(0).getValue();
              long deposit_count = Bytes.wrap(bytes).reverse().toLong();
              return Optional.of(UInt64.valueOf(deposit_count));
            });
  }

  public DepositContract getContract() {
    return contract;
  }

  private SafeFuture<String> callFunctionAtBlockNumber(String encodedFunction, UInt64 blockHeight) {
    return eth1Provider
        .ethCall(null, contract.getContractAddress(), encodedFunction, blockHeight)
        .thenApply(
            ethCall -> {
              if (ethCall.hasError()) {
                throw new Eth1RequestException(
                    "Eth1 call has failed:" + ethCall.getError().getMessage());
              } else {
                String value = ethCall.getValue();
                if (value == null) {
                  throw new Eth1RequestException(
                      "Eth1 call has failed: data at block number "
                          + blockHeight
                          + " is unavailable.");
                }
                return value;
              }
            });
  }
}
