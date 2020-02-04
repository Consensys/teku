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
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_NORMAL;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_SIM;
import static tech.pegasys.artemis.util.config.Constants.MAX_EFFECTIVE_BALANCE;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
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
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.ganache.GanacheController;
import tech.pegasys.artemis.pow.DepositContractListener;
import tech.pegasys.artemis.pow.DepositContractListenerFactory;
import tech.pegasys.artemis.pow.Eth1DataManager;
import tech.pegasys.artemis.pow.api.DepositEventChannel;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;
import tech.pegasys.artemis.util.async.DelayedExecutorAsyncRunner;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.TimeProvider;

public class PowchainService implements ServiceInterface {
  private static final Logger LOG = LogManager.getLogger();
  public static final String EVENTS = "events";
  public static final String USER_DIR = "user.dir";
  private EventBus eventBus;

  private DepositContractListener depositContractListener;

  private String depositMode;
  private String contractAddr;
  private String provider;

  private String depositSimFile;
  private TimeProvider timeProvider;
  private DepositEventChannel depositEventChannel;

  public PowchainService() {}

  @Override
  public void init(ServiceConfig config) {
    this.timeProvider = config.getTimeProvider();
    this.eventBus = config.getEventBus();
    this.depositEventChannel = config.getEventChannels().getPublisher(DepositEventChannel.class);
    this.depositMode = config.getConfig().getDepositMode();
    if (config.getConfig().getInputFile() != null)
      this.depositSimFile = System.getProperty(USER_DIR) + "/" + config.getConfig().getInputFile();
    contractAddr = config.getConfig().getContractAddr();
    provider = config.getConfig().getNodeUrl();
  }

  @Override
  public void run() {
    if (depositMode.equals(DEPOSIT_SIM) && depositSimFile == null) {
      final GanacheController controller =
          new GanacheController(Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT, 6000);
      depositContractListener =
          DepositContractListenerFactory.simulationDeployDepositContract(
              eventBus, depositEventChannel, controller, timeProvider);
      Web3j web3j = Web3j.build(new HttpService(controller.getProvider()));
      MockStartValidatorKeyPairFactory mockStartValidatorKeyPairFactory =
          new MockStartValidatorKeyPairFactory();
      List<BLSKeyPair> blsKeyPairList =
          mockStartValidatorKeyPairFactory.generateKeyPairs(0, controller.getAccounts().size());
      int i = 0;
      for (SECP256K1.KeyPair keyPair : controller.getAccounts()) {
        final DepositTransactionSender transactionSender =
            new DepositTransactionSender(
                web3j,
                depositContractListener.getContract().getContractAddress(),
                keyPair.secretKey().bytes().toHexString());

        BLSKeyPair blsKeyPair = blsKeyPairList.get(i);

        try {
          transactionSender
              .sendDepositTransaction(
                  blsKeyPair,
                  blsKeyPair.getPublicKey(),
                  UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE))
              .get();
        } catch (Exception e) {
          LOG.warn(
              "Failed to register Validator with SECP256k1 public key: " + keyPair.publicKey(), e);
        }
        i++;
      }
      Eth1DataManager eth1DataManager =
          new Eth1DataManager(
              web3j,
              eventBus,
              depositContractListener,
              new DelayedExecutorAsyncRunner(),
              timeProvider);
      eth1DataManager.start();
    } else if (depositMode.equals(DEPOSIT_SIM)) {
      try {
        Reader reader = Files.newBufferedReader(Paths.get(depositSimFile), UTF_8);
        JsonArray validatorsJSON = ((JsonArray) JsonParser.parseReader(reader));
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
      Web3j web3j = Web3j.build(new HttpService(provider));
      depositContractListener =
          DepositContractListenerFactory.eth1DepositContract(
              web3j, eventBus, depositEventChannel, contractAddr, timeProvider);
      Eth1DataManager eth1DataManager =
          new Eth1DataManager(
              web3j,
              eventBus,
              depositContractListener,
              new DelayedExecutorAsyncRunner(),
              timeProvider);
      eth1DataManager.start();
    }
  }

  @Override
  public void stop() {
    STDOUT.log(Level.DEBUG, "PowChainService.stop()");
    this.eventBus.unregister(this);
    if (depositContractListener != null) {
      depositContractListener.stop();
    }
  }
}
