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

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.abi.datatypes.Type;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.exception.Eth1RequestException;
import tech.pegasys.artemis.util.async.SafeFuture;

public class DepositContractListener {
  private final Eth1Provider eth1Provider;
  private final DepositContract contract;
  private final DepositRequestManager depositRequestManager;

  public DepositContractListener(
      Eth1Provider eth1Provider,
      DepositContract contract,
      DepositRequestManager depositRequestManager) {
    this.eth1Provider = eth1Provider;
    this.contract = contract;
    this.depositRequestManager = depositRequestManager;
  }

  public void start() {
    depositRequestManager.start();
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
    depositRequestManager.stop();
  }

  private SafeFuture<String> callFunctionAtBlockNumber(
      String encodedFunction, UnsignedLong blockHeight) {
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
