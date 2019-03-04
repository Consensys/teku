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

import static tech.pegasys.artemis.pow.contract.DepositContract.DEPOSIT_EVENT;
import static tech.pegasys.artemis.pow.contract.DepositContract.ETH2GENESIS_EVENT;

import com.google.common.eventbus.EventBus;
import io.reactivex.disposables.Disposable;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.Eth2Genesis;

public class DepositContractListener {

  private Disposable depositEventSub;
  private Disposable eth2GenesisEventSub;
  private EventBus eventBus;

  private DepositContract contract;

  public DepositContractListener(EventBus eventBus, DepositContract contract) {
    this.eventBus = eventBus;
    this.contract = contract;

    // Filter by the contract address and by begin/end blocks
    EthFilter depositEventFilter =
        new EthFilter(
                DefaultBlockParameterName.EARLIEST,
                DefaultBlockParameterName.LATEST,
                contract.getContractAddress().substring(2))
            .addSingleTopic(EventEncoder.encode(DEPOSIT_EVENT));

    EthFilter eth2GenesisEventFilter =
        new EthFilter(
                DefaultBlockParameterName.EARLIEST,
                DefaultBlockParameterName.LATEST,
                contract.getContractAddress().substring(2))
            .addSingleTopic(EventEncoder.encode(ETH2GENESIS_EVENT));

    // Subscribe to the event of a validator being registered in the
    // DepositContract
    depositEventSub =
        contract
            .depositEventFlowable(depositEventFilter)
            .subscribe(
                response -> {
                  DepositEvent event = new Deposit(response);
                  eventBus.post(event);
                });

    // Subscribe to the event when 2^14 validators have been registered in the
    // DepositContract
    eth2GenesisEventSub =
        contract
            .eth2GenesisEventFlowable(eth2GenesisEventFilter)
            .subscribe(
                response -> {
                  Eth2Genesis event = new Eth2Genesis(response);
                  eventBus.post(event);
                });
  }

  public DepositContract getContract() {
    return contract;
  }
}
