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

import com.google.common.eventbus.EventBus;
import io.reactivex.disposables.Disposable;
import org.web3j.abi.EventEncoder;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.ganache.GanacheController;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.pow.contract.ValidatorRegistrationContract;
import tech.pegasys.artemis.pow.contract.ValidatorRegistrationContract.DepositEventResponse;
import tech.pegasys.artemis.pow.contract.ValidatorRegistrationContract.Eth2GenesisEventResponse;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.Eth2Genesis;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ResourceBundle;

import static tech.pegasys.artemis.pow.contract.ValidatorRegistrationContract.DEPOSIT_EVENT;
import static tech.pegasys.artemis.pow.contract.ValidatorRegistrationContract.ETH2GENESIS_EVENT;

public class ValidatorRegistrationClient {

  private final EventBus eventBus;

  public Disposable depositEventSub;
  public Disposable eth2GenesisEventSub;

  private static final ResourceBundle rb = ResourceBundle.getBundle("config");
  boolean debug = (rb.getString("debug").compareTo("true") == 0);

  public ValidatorRegistrationClient(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  //  skip the foreplay
  //  public void simulatePowChain() {
  //    // TODO: Move this simulated startup logic
  //    ValidatorRegistrationContract.ChainStartEventResponse response =
  //        new ValidatorRegistrationContract.ChainStartEventResponse();
  //    chainStarted(response);
  //  }

  @SuppressWarnings("unchecked")
  public void listenToPoWChain() {
    ValidatorRegistrationContract contract = null;
    if (debug) {
      GanacheController ganacheController = null;
      try {
        ganacheController = new GanacheController(25, 6000);
      } catch (IOException e) {
        System.err.println(e);
      }
      contract =
          contractInit(
              ganacheController.getProvider(),
              ganacheController.getAccounts().get(0).getPrivateKey(),
              debug);
    } else {
      String provider = rb.getString("hostname") + ":" + rb.getString("port");
      contract = contractInit(provider, rb.getString("key"), debug);
    }

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
    // ValidatorRegistrationContract
    depositEventSub =
        contract
            .depositEventFlowable(depositEventFilter)
            .subscribe(
                response -> {
                  DepositEvent(response);
                });

    // Subscribe to the event when 2^14 validators have been registered in the
    // ValidatorRegistrationContract
    eth2GenesisEventSub =
        contract
            .eth2GenesisEventFlowable(eth2GenesisEventFilter)
            .subscribe(
                response -> {
                  Eth2GenesisEvent(response);
                });

    if (debug) sendDepositTransactions(contract);
  }

  private static ValidatorRegistrationContract contractInit(
      String provider, String privateKey, boolean debug) {
    Web3j web3j = Web3j.build(new HttpService(provider));
    Credentials credentials = Credentials.create(privateKey);

    ContractGasProvider contractGasProvider = new DefaultGasProvider();
    ValidatorRegistrationContract contract = null;
    if (debug) {
      try {
        contract =
            ValidatorRegistrationContract.deploy(web3j, credentials, contractGasProvider).send();
      } catch (Exception e) {
        System.err.println(e);
      }
    } else {
      try {
        contract =
            ValidatorRegistrationContract.load(
                rb.getString("vrc_contract_address"), web3j, credentials, contractGasProvider);
      } catch (Exception e) {
        System.err.println(e);
      }
    }
    return contract;
  }

  // method only used for debugging
  // calls a deposit transaction on the ValidatorRegistrationContract every 10 seconds
  // simulate depositors
  private void sendDepositTransactions(ValidatorRegistrationContract contract) {
    byte[] depositParams = new byte[512];
    for (int i = 0; i < 512; i++) depositParams[i] = (byte) 0x0;

    while (true) {

      System.err.println("Deposit Transaction Sent");
      try {
        contract.deposit(depositParams, new BigInteger("1000000000000000000")).send();
      } catch (Exception e) {
        System.out.println(e);
      }

      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        System.out.println(e);
      }
    }
  }

  public void DepositEvent(DepositEventResponse response) {
    DepositEvent event = new Deposit(response);
    this.eventBus.post(event);
  }

  public void Eth2GenesisEvent(Eth2GenesisEventResponse response) {
    Eth2GenesisEvent event = new Eth2Genesis(response);
    this.eventBus.post(event);
  }
}
