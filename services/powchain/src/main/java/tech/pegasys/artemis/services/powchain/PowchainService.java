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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_domain;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_NORMAL;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_SIM;

import com.google.common.eventbus.EventBus;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.crypto.SECP256K1;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.ganache.GanacheController;
import tech.pegasys.artemis.pow.DepositContractListener;
import tech.pegasys.artemis.pow.DepositContractListenerFactory;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.client.Validator;
import tech.pegasys.artemis.validator.client.ValidatorClientUtil;

public class PowchainService implements ServiceInterface {
  private static final Logger LOG = LogManager.getLogger();
  public static final String EVENTS = "events";
  public static final String USER_DIR = "user.dir";
  private EventBus eventBus;

  private GanacheController controller;
  private DepositContractListener listener;

  private String depositMode;
  private String contractAddr;
  private String provider;

  private String depositSimFile;

  public PowchainService() {}

  @Override
  public void init(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.depositMode = config.getConfig().getDepositMode();
    if (config.getConfig().getInputFile() != null)
      this.depositSimFile = System.getProperty(USER_DIR) + "/" + config.getConfig().getInputFile();
    contractAddr = config.getConfig().getContractAddr();
    provider = config.getConfig().getNodeUrl();
  }

  @Override
  public void run() {
    if (depositMode.equals(DEPOSIT_SIM) && depositSimFile == null) {
      controller = new GanacheController(Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT, 6000);
      listener =
          DepositContractListenerFactory.simulationDeployDepositContract(eventBus, controller);
      Web3j web3j = Web3j.build(new HttpService(controller.getProvider()));
      DefaultGasProvider gasProvider = new DefaultGasProvider();
      MockStartValidatorKeyPairFactory mockStartValidatorKeyPairFactory =
          new MockStartValidatorKeyPairFactory();
      MockStartDepositGenerator mockStartDepositGenerator = new MockStartDepositGenerator();
      List<BLSKeyPair> blsKeyPairList =
          mockStartValidatorKeyPairFactory.generateKeyPairs(0, controller.getAccounts().size());
      List<DepositData> depositDataList = mockStartDepositGenerator.createDeposits(blsKeyPairList);
      int i = 0;
      for (SECP256K1.KeyPair keyPair : controller.getAccounts()) {
        DepositData depositData = depositDataList.get(i);
        BLSKeyPair blsKeyPair = blsKeyPairList.get(i);
        Validator validator =
            new Validator(depositData.getWithdrawal_credentials(), blsKeyPair, keyPair);

        BLSSignature sig =
            BLSSignature.sign(
                blsKeyPairList.get(i),
                depositData.signing_root("signature"),
                compute_domain(Constants.DOMAIN_DEPOSIT));
        try {
          ValidatorClientUtil.registerValidatorEth1(
              validator,
              depositData.getAmount().longValue(),
              listener.getContract().getContractAddress(),
              web3j,
              gasProvider,
              depositData.hash_tree_root(),
              sig);
        } catch (Exception e) {
          LOG.warn(
              "Failed to register Validator with SECP256k1 public key: " + keyPair.publicKey(), e);
        }
        i++;
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
      } catch (IOException e) {
        LOG.error("Failed to process deposit events", e);
      }
    } else if (depositMode.equals(DEPOSIT_NORMAL)) {
      listener =
          DepositContractListenerFactory.eth1DepositContract(eventBus, provider, contractAddr);
    }
  }

  @Override
  public void stop() {
    STDOUT.log(Level.DEBUG, "PowChainService.stop()");
    this.eventBus.unregister(this);
    if (listener != null) {
      listener.stop();
    }
  }
}
