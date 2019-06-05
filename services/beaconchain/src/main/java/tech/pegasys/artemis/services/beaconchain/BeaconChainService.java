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
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.service.serviceutils.ServiceInterface;
import tech.pegasys.artemis.statetransition.StateProcessor;
import tech.pegasys.artemis.storage.ChainStorage;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.time.Timer;
import tech.pegasys.artemis.util.time.TimerFactory;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class BeaconChainService implements ServiceInterface {

  private EventBus eventBus;
  private Timer timer;
  private StateProcessor stateProcessor;
  private ValidatorCoordinator validatorCoordinator;
  private ChainStorageClient store;

  public BeaconChainService() {}

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void init(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    try {
      this.timer =
          new TimerFactory()
              .create(
                  config.getConfig().getTimer(),
                  new Object[] {this.eventBus, 5, Constants.SECONDS_PER_SLOT},
                  new Class[] {EventBus.class, Integer.class, Integer.class});
    } catch (IllegalArgumentException e) {
      System.exit(1);
    }
    this.store = ChainStorage.Create(ChainStorageClient.class, eventBus);
    this.stateProcessor = new StateProcessor(config, store);
    this.validatorCoordinator = new ValidatorCoordinator(config);
  }

  @Override
  public void run() {}

  @Override
  public void stop() {
    this.timer.stop();
    this.eventBus.unregister(this);
  }

  @Subscribe
  public void afterChainStart(Boolean chainStarted) {
    if (chainStarted) {
      // slot scheduler fires an event that tells us when it is time for a new slot
      this.timer.start();
    }
  }
}
