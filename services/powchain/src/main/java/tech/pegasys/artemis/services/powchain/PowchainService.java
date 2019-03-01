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

import static tech.pegasys.artemis.datastructures.Constants.DEPOSIT_DATA_SIZE;
import static tech.pegasys.artemis.datastructures.Constants.SIM_DEPOSIT_VALUE;

import com.google.common.eventbus.EventBus;
import java.math.BigInteger;
import net.consensys.cava.bytes.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.ganache.GanacheController;
import tech.pegasys.artemis.pow.DepositContractListener;
import tech.pegasys.artemis.pow.DepositContractListenerFactory;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.services.ServiceInterface;

public class PowchainService implements ServiceInterface {

  private EventBus eventBus;
  private static final Logger LOG = LogManager.getLogger();

  private GanacheController controller;
  private DepositContractListener listener;

  private boolean simulation = true;
  String privateKey;
  String provider;

  public PowchainService() {}

  @Override
  public void init(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  @Override
  public void run() {
    // if(simulation){
    controller = new GanacheController(25, 6000);
    listener = DepositContractListenerFactory.simulationDepositContract(eventBus, controller);
    simulateDepositActivity(listener.getContract());
    // }
    // else listener = DepositContractListenerFactory.eth1DepositContract(this, provider,
    // privateKey);
  }

  // method only used for debugging
  // calls a deposit transaction on the DepositContract every 10 seconds
  // simulate depositors
  private static void simulateDepositActivity(DepositContract contract) {
    Bytes bytes = Bytes.random(DEPOSIT_DATA_SIZE);
    while (true) {
      try {
        contract.deposit(bytes.toArray(), new BigInteger(SIM_DEPOSIT_VALUE)).send();
      } catch (Exception e) {
        LOG.warn(
            "PowchainService.simulateDepositActivity: Exception thrown when attempting to send a deposit transaction during a deposit simulation\n"
                + e);
      }
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        LOG.warn(
            "PowchainService.simulateDepositActivity: Exception thrown when attempting a thread sleep during a deposit simulation\n"
                + e);
      }
    }
  }

  @Override
  public void stop() {
    this.eventBus.unregister(this);
  }
}
