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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.datastructures.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_tick;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Date;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

public class TimingProcessor {

  private final EventBus eventBus;
  private ChainStorageClient chainStorageClient;
  private UnsignedLong nodeSlot = UnsignedLong.ZERO;
  private static final ALogger STDOUT = new ALogger("stdout");

  public TimingProcessor(ServiceConfig config, ChainStorageClient chainStorageClient) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.chainStorageClient = chainStorageClient;
  }

  @Subscribe
  private void onTick(Date date) {
    try {
      on_tick(chainStorageClient.getStore(), UnsignedLong.valueOf(date.getTime() / 1000));
      if (chainStorageClient
              .getStore()
              .getTime()
              .compareTo(
                  chainStorageClient
                      .getGenesisTime()
                      .plus(nodeSlot.times(UnsignedLong.valueOf(SECONDS_PER_SLOT))))
          >= 0) {
        this.eventBus.post(new SlotEvent(nodeSlot));
        STDOUT.log(Level.INFO, "******* Slot Event *******", ALogger.Color.WHITE);
        STDOUT.log(Level.INFO, "Node slot:                             " + nodeSlot);
        nodeSlot = nodeSlot.plus(UnsignedLong.ONE);
        Thread.sleep(SECONDS_PER_SLOT * 1000 / 2);
        this.eventBus.post(new ValidatorAssignmentEvent());
      }
    } catch (InterruptedException e) {
      STDOUT.log(Level.FATAL, "onTick: " + e.toString());
    }
  }
}
