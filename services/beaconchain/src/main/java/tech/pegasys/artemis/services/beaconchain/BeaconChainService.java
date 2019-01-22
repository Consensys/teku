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

package tech.pegasys.artemis.services.beaconchain;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.pow.api.ChainStartEvent;
import tech.pegasys.artemis.pow.api.ValidatorRegistrationEvent;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.state.SlotScheduler;
import tech.pegasys.artemis.state.StateTreeManager;

public class BeaconChainService implements ServiceInterface {

  private EventBus eventBus;
  private ScheduledExecutorService scheduler;
  private static final Logger LOG = LogManager.getLogger();
  private StateTreeManager stateTreeManager;

  public BeaconChainService() {}

  @Override
  public void init(EventBus eventBus) {
    this.eventBus = eventBus;
    this.scheduler = Executors.newScheduledThreadPool(1);
    this.stateTreeManager = new StateTreeManager(this.eventBus);
    this.eventBus.register(this);
  }

  @Override
  public void run() {
    // slot scheduler fires an event that tells us when it is time for a new slot
    int initialDelay = 0;
    scheduler.scheduleAtFixedRate(
        new SlotScheduler(this.eventBus), initialDelay, Constants.SLOT_DURATION, TimeUnit.SECONDS);
  }

  @Override
  public void stop() {
    this.scheduler.shutdown();
    this.eventBus.unregister(this);
  }

  @Subscribe
  public void onChainStarted(ChainStartEvent event) {
    LOG.info("ChainStart Event Detected");
  }

  @Subscribe
  public void onValidatorRegistered(ValidatorRegistrationEvent event) {
    LOG.info("Validator Registration Event detected");
    // LOG.info("   Validator Number: " + validatorRegisteredEvent.getInfo());
  }
}
