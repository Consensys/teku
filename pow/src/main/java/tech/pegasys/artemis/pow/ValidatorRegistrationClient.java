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
import java.math.BigInteger;
import java.util.ResourceBundle;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import rx.Subscription;
import tech.pegasys.artemis.pow.api.ChainStartEvent;
import tech.pegasys.artemis.pow.api.ValidatorRegistrationEvent;
import tech.pegasys.artemis.pow.contract.ValidatorRegistrationContract;
import tech.pegasys.artemis.pow.event.ChainStart;
import tech.pegasys.artemis.pow.event.ValidatorRegistration;

public class ValidatorRegistrationClient {

  private final EventBus eventBus;

  public Subscription eth1DepositSub;
  public Subscription chainStartSub;

  private static final ResourceBundle rb = ResourceBundle.getBundle("config");
  boolean debug = (rb.getString("debug").compareTo("true") == 0);

  public ValidatorRegistrationClient(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public void listenToPoWChain() {

    // Setup provider and credentials
    Web3j web3j = Web3j.build(new HttpService(rb.getString("provider")));
    Credentials credentials = WalletUtils.loadBip39Credentials(null, rb.getString("mnemonic"));

    ValidatorRegistrationContract contract = null;

    if (debug) contract = deployVRC(web3j, credentials);
    else contract = getDeployedVRC(web3j, credentials);

    // Filter by the contract address and by begin/end blocks
    EthFilter filter =
        new EthFilter(
            DefaultBlockParameterName.EARLIEST,
            DefaultBlockParameterName.LATEST,
            contract.getContractAddress().substring(2));

    // Subscribe to the event of a validator being registered in the ValidatorRegistrationContract
    eth1DepositSub =
        contract
            .eth1DepositEventObservable(filter)
            .subscribe(
                response -> {
                  validatorRegistered(response);
                });

    // Subscribe to the event when 2^14 validators have been registered in the
    // ValidatorRegistrationContract
    chainStartSub =
        contract
            .chainStartEventObservable(filter)
            .subscribe(
                response -> {
                  chainStarted(response);
                });

    if (debug) sendDepositTransactions(contract);
  }

  // method only used for debugging
  // calls a deposit transaction on the ValidatorRegistrationContract every 10 seconds
  private void sendDepositTransactions(ValidatorRegistrationContract contract) {
    byte[] depositParams = new byte[2048];
    for (int i = 0; i < 2048; i++) depositParams[i] = (byte) 0x0;

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

  public ValidatorRegistrationContract deployVRC(Web3j web3j, Credentials credentials) {

    ContractGasProvider contractGasProvider = new DefaultGasProvider();
    ValidatorRegistrationContract contract = null;
    try {
      contract =
          ValidatorRegistrationContract.deploy(web3j, credentials, contractGasProvider).send();
    } catch (Exception e) {
      System.err.println(e);
    }

    return contract;
  }

  public ValidatorRegistrationContract getDeployedVRC(Web3j web3j, Credentials credentials) {
    ContractGasProvider contractGasProvider = new DefaultGasProvider();
    ValidatorRegistrationContract contract = null;
    try {
      contract =
          ValidatorRegistrationContract.load(
              rb.getString("vrc_contract_address"), web3j, credentials, contractGasProvider);
    } catch (Exception e) {
      System.err.println(e);
    }

    return contract;
  }

  public void validatorRegistered(ValidatorRegistrationContract.Eth1DepositEventResponse response) {
    ValidatorRegistrationEvent event = new ValidatorRegistration(response);
    this.eventBus.post(event);
  }

  public void chainStarted(ValidatorRegistrationContract.ChainStartEventResponse response) {
    ChainStartEvent event = new ChainStart(response);
    this.eventBus.post(event);
  }
}
