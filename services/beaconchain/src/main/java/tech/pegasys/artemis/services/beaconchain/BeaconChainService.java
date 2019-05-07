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
import tech.pegasys.artemis.services.ServiceConfig;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.statetransition.SlotScheduler;
import tech.pegasys.artemis.statetransition.StateProcessor;
import tech.pegasys.artemis.util.time.JavaTimer;
import tech.pegasys.artemis.util.time.QuartzTimer;
import tech.pegasys.artemis.util.time.Timer;

public class BeaconChainService implements ServiceInterface {

  private EventBus eventBus;
  private Timer timer;
  private StateProcessor stateProcessor;

  public BeaconChainService() {}

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void init(ServiceConfig config) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    if (true) {
      this.timer =
          new QuartzTimer(SlotScheduler.class, this.eventBus, 5, Constants.SECONDS_PER_SLOT);
    } else {
      this.timer = new JavaTimer(SlotScheduler.class, this.eventBus, 0, Constants.SECONDS_PER_SLOT);
    }
    this.stateProcessor =
        new StateProcessor(this.eventBus, config.getConfig(), config.getKeyPair().publicKey());
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
