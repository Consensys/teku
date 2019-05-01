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

import com.google.common.eventbus.EventBus;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import net.consensys.cava.bytes.Bytes;
import org.apache.logging.log4j.Level;
import org.web3j.protocol.core.methods.response.Log;
import tech.pegasys.artemis.ganache.GanacheController;
import tech.pegasys.artemis.pow.DepositContractListener;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.contract.DepositContract.Eth2GenesisEventResponse;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.Eth2Genesis;
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.util.alogger.ALogger;

public class PowchainService implements ServiceInterface {

  public static final String SIM_DEPOSIT_VALUE = "1000000000000000000";
  public static final int DEPOSIT_DATA_SIZE = 512;

  private EventBus eventBus;
  private static final ALogger LOG = new ALogger();

  private GanacheController controller;
  private DepositContractListener listener;

  private boolean depositSimulation;
  String privateKey;
  String provider;

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
      JsonParser parser = new JsonParser();
      try {
        String simFile = System.getProperty("user.dir") + "/" + "validator_test_data.json";
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

  // method only used for debugging
  // calls a deposit transaction on the DepositContract every 10 seconds
  // simulate depositors
  private static void simulateDepositActivity(DepositContract contract, EventBus eventBus) {
    Bytes bytes = Bytes.random(DEPOSIT_DATA_SIZE);
    while (true) {
      try {
        contract.deposit(bytes.toArray(), new BigInteger(SIM_DEPOSIT_VALUE)).send();
      } catch (Exception e) {
        LOG.log(
            Level.WARN,
            "PowchainService.simulateDepositActivity: Exception thrown when attempting to send a deposit transaction during a deposit simulation\n"
                + e);
      }
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        LOG.log(
            Level.WARN,
            "PowchainService.simulateDepositActivity: Exception thrown when attempting a thread sleep during a deposit simulation\n"
                + e);
      }
    }
  }
}
