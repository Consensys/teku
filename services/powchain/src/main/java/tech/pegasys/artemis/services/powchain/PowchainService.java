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

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.artemis.datastructures.Constants.DEPOSIT_NORMAL;
import static tech.pegasys.artemis.datastructures.Constants.DEPOSIT_SIM;

import com.google.common.eventbus.EventBus;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.ganache.GanacheController;
import tech.pegasys.artemis.pow.DepositContractListener;
import tech.pegasys.artemis.pow.DepositContractListenerFactory;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.validator.client.Validator;
import tech.pegasys.artemis.validator.client.ValidatorClientUtil;

public class PowchainService implements ServiceInterface {

  public static final String SIM_DEPOSIT_VALUE_GWEI = "32000000000";
  public static final String EVENTS = "events";
  public static final String ROOT = "root";
  public static final String USER_DIR = "user.dir";
  private EventBus eventBus;
  private static final ALogger LOG = new ALogger();

  private GanacheController controller;
  private DepositContractListener listener;

  private String depositMode;
  private String contractAddr;
  private String provider;

  private String depositSimFile;
  private int validatorCount;
  private int nodeCount;

  public PowchainService() {}

  @Override
  public void init(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.depositMode = config.getConfig().getDepositMode();
    if (config.getConfig().getInputFile() != null)
      this.depositSimFile = System.getProperty(USER_DIR) + "/" + config.getConfig().getInputFile();
    validatorCount = config.getConfig().getNumValidators();
    nodeCount = config.getConfig().getNumNodes();
    contractAddr = config.getConfig().getContractAddr();
    provider = config.getConfig().getNodeUrl();
  }

  @Override
  public void run() {
    if (depositMode.equals(DEPOSIT_SIM) && depositSimFile == null) {
      controller = new GanacheController(10, 6000);
      listener =
          DepositContractListenerFactory.simulationDeployDepositContract(eventBus, controller);
      Web3j web3j = Web3j.build(new HttpService(controller.getProvider()));
      DefaultGasProvider gasProvider = new DefaultGasProvider();
      for (SECP256K1.KeyPair keyPair : controller.getAccounts()) {
        Validator validator = new Validator(Bytes32.random(), KeyPair.random(), keyPair);
        try {
          ValidatorClientUtil.registerValidatorEth1(
              validator,
              Long.parseLong(SIM_DEPOSIT_VALUE_GWEI),
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
    } else if (depositMode.equals(DEPOSIT_SIM) && depositSimFile != null) {
      JsonParser parser = new JsonParser();
      try {
        Reader reader = Files.newBufferedReader(Paths.get(depositSimFile), UTF_8);
        JsonArray validatorsJSON = ((JsonArray) parser.parse(reader));
        validatorsJSON.forEach(
            object -> {
              if (object.getAsJsonObject().get(EVENTS) != null) {
                JsonArray events = object.getAsJsonObject().get(EVENTS).getAsJsonArray();
                events.forEach(
                    event -> {
                      Deposit deposit = DepositUtil.convertJsonDataToEventDeposit(event);
                      eventBus.post(deposit);
                    });
              }
            });
      } catch (FileNotFoundException e) {
        LOG.log(Level.ERROR, e.getMessage());
      } catch (IOException e) {
        LOG.log(Level.ERROR, e.getMessage());
      }
    } else if (depositMode.equals(DEPOSIT_NORMAL)) {
      listener =
          DepositContractListenerFactory.eth1DepositContract(eventBus, provider, contractAddr);
    }
  }

  @Override
  public void stop() {
    this.eventBus.unregister(this);
  }
}
