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
import java.util.stream.Collectors;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.pow.contract.DepositContract;

public class DepositEventsAccessor {
  private final Eth1Provider eth1Provider;
  private final String contractAddress;

  public DepositEventsAccessor(Eth1Provider eth1Provider, String contractAddress) {
    this.eth1Provider = eth1Provider;
    this.contractAddress = contractAddress;
  }

  public SafeFuture<List<DepositContract.DepositEventEventResponse>> depositEventInRange(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    final EthFilter filter = new EthFilter(startBlock, endBlock, this.contractAddress);
    filter.addSingleTopic(EventEncoder.encode(DepositContract.DEPOSITEVENT_EVENT));
    return SafeFuture.of(
        eth1Provider
            .ethGetLogs(filter)
            .thenApply(
                logs ->
                    logs.stream()
                        .map(log -> (Log) log.get())
                        .map(this::convertLogToDepositEventEventResponse)
                        .collect(Collectors.toList())));
  }

  private DepositContract.DepositEventEventResponse convertLogToDepositEventEventResponse(
      final Log log) {
    Contract.EventValuesWithLog eventValues = DepositContract.staticExtractDepositEventWithLog(log);
    DepositContract.DepositEventEventResponse typedResponse =
        new DepositContract.DepositEventEventResponse();
    typedResponse.log = log;
    typedResponse.pubkey = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
    typedResponse.withdrawal_credentials =
        (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
    typedResponse.amount = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
    typedResponse.signature = (byte[]) eventValues.getNonIndexedValues().get(3).getValue();
    typedResponse.index = (byte[]) eventValues.getNonIndexedValues().get(4).getValue();
    return typedResponse;
  }
}
