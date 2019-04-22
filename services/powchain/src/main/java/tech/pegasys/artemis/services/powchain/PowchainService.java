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

package tech.pegasys.artemis.services.powchain;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.nio.charset.Charset;
import java.util.Collections;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.ganache.GanacheController;
import tech.pegasys.artemis.pow.DepositContractListener;
import tech.pegasys.artemis.pow.DepositContractListenerFactory;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.pow.contract.DepositContract.Eth2GenesisEventResponse;
import tech.pegasys.artemis.pow.event.Eth2Genesis;
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.validator.client.Validator;
import tech.pegasys.artemis.validator.client.ValidatorClient;

public class PowchainService implements ServiceInterface {

  public static final String SIM_DEPOSIT_VALUE_GWEI = "32000000000";
  private EventBus eventBus;
  private static final ALogger LOG = new ALogger();

  private GanacheController controller;
  DepositContractListener listener;

  private boolean depositSimulation;

  public PowchainService() {
    depositSimulation = false;
  }

  @Override
  public void init(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.depositSimulation = config.getCliArgs().isSimulation();
  }

  @Override
  public void run() {

    if (depositSimulation) {
      controller = new GanacheController(16384, 6000);
      listener =
          DepositContractListenerFactory.simulationDeployDepositContract(eventBus, controller);
      Web3j web3j = Web3j.build(new HttpService(controller.getProvider()));
      DefaultGasProvider gasProvider = new DefaultGasProvider();
      for (SECP256K1.KeyPair keyPair : controller.getAccounts()) {
        Validator validator = new Validator(Bytes32.random(), KeyPair.random(), keyPair);
        try {
          ValidatorClient.registerValidatorEth1(
              validator,
              UnsignedLong.valueOf(SIM_DEPOSIT_VALUE_GWEI),
              listener.getContract().getContractAddress(),
              web3j,
              gasProvider);
        } catch (Exception e) {
          LOG.log(
              Level.WARN,
              "Failed to register Validator with SECP256k1 public key: "
                  + keyPair.publicKey()
                  + " : "
                  + e);
        }
      }
    } else {
      Eth2GenesisEventResponse response = new Eth2GenesisEventResponse();
      response.log =
          new Log(true, "1", "2", "3", "4", "5", "6", "7", "8", Collections.singletonList("9"));
      response.time = "time".getBytes(Charset.defaultCharset());
      response.deposit_root = "root".getBytes(Charset.defaultCharset());
      Eth2GenesisEvent event = new Eth2Genesis(response);
      this.eventBus.post(event);
    }
  }

  @Override
  public void stop() {
    this.eventBus.unregister(this);
  }

  @Subscribe
  public void onDepositEvent(DepositEvent event) {
    // stubbed for implementation on next PR
  }
}
