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

import static com.google.common.base.Charsets.UTF_8;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.ganache.GanacheController;
import tech.pegasys.artemis.pow.DepositContractListener;
import tech.pegasys.artemis.pow.DepositContractListenerFactory;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.contract.DepositContract.Eth2GenesisEventResponse;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.Eth2Genesis;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.validator.client.DepositSimulation;
import tech.pegasys.artemis.validator.client.Validator;
import tech.pegasys.artemis.validator.client.ValidatorClientUtil;

public class PowchainService implements ServiceInterface {

  public static final String SIM_DEPOSIT_VALUE_GWEI = "32000000000";
  private EventBus eventBus;
  private static final ALogger LOG = new ALogger();

  private GanacheController controller;
  private DepositContractListener listener;

  private boolean depositSimulation;

  List<DepositSimulation> simulations;
  private String simFile;

  public PowchainService() {
    depositSimulation = false;
  }

  @Override
  public void init(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.depositSimulation = config.getConfig().isSimulation();
    if (config.getConfig().getInputFile() != null)
      this.simFile = System.getProperty("user.dir") + "/" + config.getConfig().getInputFile();
  }

  @Override
  public void run() {
    if (depositSimulation && simFile == null) {
      controller = new GanacheController(10, 6000);
      listener =
          DepositContractListenerFactory.simulationDeployDepositContract(eventBus, controller);
      Web3j web3j = Web3j.build(new HttpService(controller.getProvider()));
      DefaultGasProvider gasProvider = new DefaultGasProvider();
      simulations = new ArrayList<DepositSimulation>();
      for (SECP256K1.KeyPair keyPair : controller.getAccounts()) {
        Validator validator = new Validator(Bytes32.random(), KeyPair.random(), keyPair);
        simulations.add(
            new DepositSimulation(
                validator,
                ValidatorClientUtil.generateDepositData(
                    validator.getBlsKeys(),
                    validator.getWithdrawal_credentials(),
                    Long.parseLong(SIM_DEPOSIT_VALUE_GWEI))));
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
    } else if (depositSimulation && simFile != null) {
      JsonParser parser = new JsonParser();
      try {
        Reader reader = Files.newBufferedReader(Paths.get(simFile), UTF_8);
        JsonArray validatorsJSON = ((JsonArray) parser.parse(reader));
        validatorsJSON.forEach(
            object -> {
              if (object.getAsJsonObject().get("events") != null) {
                JsonArray events = object.getAsJsonObject().get("events").getAsJsonArray();
                events.forEach(
                    event -> {
                      DepositContract.DepositEventResponse response =
                          new DepositContract.DepositEventResponse();
                      response.data =
                          Bytes.fromHexString(event.getAsJsonObject().get("data").getAsString())
                              .toArray();
                      response.merkle_tree_index =
                          Bytes.fromHexString(
                                  event.getAsJsonObject().get("merkle_tree_index").getAsString())
                              .toArray();
                      Deposit deposit = new Deposit(response);
                      eventBus.post(deposit);
                    });
              } else {
                JsonObject event = object.getAsJsonObject();
                DepositContract.Eth2GenesisEventResponse response =
                    new DepositContract.Eth2GenesisEventResponse();
                response.deposit_root =
                    Bytes.fromHexString(event.getAsJsonObject().get("deposit_root").getAsString())
                        .toArray();
                response.deposit_count =
                    Bytes.ofUnsignedInt(
                            event.getAsJsonObject().get("deposit_count").getAsInt(),
                            ByteOrder.BIG_ENDIAN)
                        .toArray();
                response.time =
                    Bytes.ofUnsignedLong(
                            event.getAsJsonObject().get("time").getAsLong(), ByteOrder.BIG_ENDIAN)
                        .toArray();
                Eth2Genesis eth2Genesis = new Eth2Genesis(response);
                eventBus.post(eth2Genesis);
              }
            });
      } catch (FileNotFoundException e) {
        LOG.log(Level.ERROR, e.getMessage());
      } catch (IOException e) {
        LOG.log(Level.ERROR, e.getMessage());
      }
    } else {
      Eth2GenesisEventResponse response = new Eth2GenesisEventResponse();
      response.log =
          new Log(true, "1", "2", "3", "4", "5", "6", "7", "8", Collections.singletonList("9"));
      response.time = Bytes.ofUnsignedLong(UnsignedLong.ONE.longValue()).toArray();
      response.deposit_count = Bytes.ofUnsignedLong(UnsignedLong.ONE.longValue()).toArray();
      response.deposit_root = "root".getBytes(Charset.defaultCharset());
      Eth2Genesis eth2Genesis = new tech.pegasys.artemis.pow.event.Eth2Genesis(response);
      this.eventBus.post(eth2Genesis);
    }
  }

  @Override
  public void stop() {
    this.eventBus.unregister(this);
  }

  @Subscribe
  public void onDepositEvent(Eth2Genesis event) {
    LOG.log(Level.INFO, event.toString());
  }

  private DepositSimulation attributeDepositToSimulation(Deposit deposit) {
    for (int i = 0; i < simulations.size(); i++) {
      DepositSimulation simulation = simulations.get(i);
      if (simulation
          .getValidator()
          .getWithdrawal_credentials()
          .equals(deposit.getWithdrawal_credentials())) {
        simulation.getDeposits().add(deposit);
        return simulation;
      }
      ;
    }
    return null;
  }
}
